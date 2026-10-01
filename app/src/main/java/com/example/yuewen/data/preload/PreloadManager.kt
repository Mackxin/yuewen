package com.example.yuewen.data.preload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.repository.NewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 一次预加载的进度快照。
 *
 * @param running 是否正在跑
 * @param done 已处理篇数（含抓失败的）
 * @param total 本轮总数
 * @param saved 真正抓到并入库的篇数
 */
data class PreloadProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val saved: Int = 0
)

/**
 * 离线预加载调度器（v1.9）。
 *
 * **为什么要独立成一个 App 级对象**：这个动作有三个入口 ——
 * 设置页手动点「立即缓存全部」、刷新完自动跑一轮、后台定时刷新完也跑一轮，
 * 而且设置页还要显示实时进度。状态散在各个 ViewModel 里就对不上了，
 * 挂到 Application 上大家共用一份最省心。
 *
 * 它还负责「值不值得跑」的判断：
 * - **自动模式**：开关关着就不跑；勾了「仅 Wi-Fi」而不在 Wi-Fi 上也不跑。
 * - **手动模式**（用户明确点了按钮）：不受这两个开关限制 —— 点了就是要跑。
 */
class PreloadManager(
    private val repo: NewsRepository,
    private val settings: SettingsRepository,
    context: Context
) {

    /** 只留 Application Context：它活得比任何界面都久。 */
    private val appContext: Context = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _progress = MutableStateFlow(PreloadProgress())
    val progress: StateFlow<PreloadProgress> = _progress.asStateFlow()

    private var job: Job? = null

    /**
     * 轮次编号（v2.3）。
     *
     * 修的是一个很隐蔽的「进度条闪一下就没了」：
     * [cancel] 会立刻把 `running` 置 false，用户马上又点「立即缓存全部」开新一轮；
     * 而**上一轮的协程还没真正结束**，它的收尾（把 `running` 写回 false）可能排在
     * 新一轮启动之后执行 —— 于是新一轮刚亮起来的进度条被旧协程按灭了。
     *
     * 现在每轮开跑都领一个号，收尾前先对号：号不对说明自己已经过期，直接不写状态。
     * 用 AtomicInteger 是因为 [cancel] 在 UI 线程被调，而收尾在 IO 线程，
     * 简单 `var` 在这里会有可见性问题。
     */
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    /** 当前是不是连着 Wi-Fi / 有线网。用来实现「仅在 Wi-Fi 下预加载」。 */
    fun isOnWifi(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
            val transportOk = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            transportOk && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            // 拿不到网络信息时保守返回 false（宁可不跑，也不偷跑流量）
            false
        }
    }

    /** 手动：「立即缓存全部正文」。用户点名要跑，不看自动开关。 */
    fun startNow() {
        if (_progress.value.running) return
        val gen = generation.incrementAndGet()
        job = scope.launch { runInternal(force = true, limit = MANUAL_LIMIT, gen = gen) }
    }

    /** 自动：刷新完之后调用。开关关着 / 不在 Wi-Fi 时自己会跳过，调用方不用判断。 */
    fun maybeAutoStart() {
        if (_progress.value.running) return
        val gen = generation.incrementAndGet()
        job = scope.launch { runInternal(force = false, limit = AUTO_LIMIT, gen = gen) }
    }

    /**
     * 供后台 Worker 调用：**在调用方的协程里跑完再返回**。
     *
     * 不能在这里用 [scope]：Worker 的 `doWork()` 一返回，系统就认为活干完了，
     * 进程随时可能被回收，扔给独立 scope 的任务会被腰斩。
     *
     * v2.3：补上 `running` 判断 —— 以前它是唯一不判断的入口，
     * 后台定时刷新撞上用户手动点「立即缓存全部」时会同时跑两轮，
     * 请求量翻倍、两边互相覆盖进度。
     */
    suspend fun runAutoHere() {
        if (_progress.value.running) return
        val gen = generation.incrementAndGet()
        runInternal(force = false, limit = AUTO_LIMIT, gen = gen)
    }

    /** 取消本轮（正在抓的那几篇会尽快收尾）。 */
    fun cancel() {
        // 先让当前轮次作废，再取消协程：这样它稍后执行的收尾不会再写状态
        generation.incrementAndGet()
        job?.cancel()
        job = null
        _progress.value = _progress.value.copy(running = false)
    }

    private suspend fun runInternal(force: Boolean, limit: Int, gen: Int) {
        if (!force) {
            if (!settings.preloadAutoFlow.first()) return
            if (settings.preloadWifiOnlyFlow.first() && !isOnWifi()) return
        }
        _progress.value = PreloadProgress(running = true)
        // shouldStop 是个普通（非 suspend）lambda，不能在里边调 currentCoroutineContext()，
        // 所以在进入循环前先把上下文抓出来，之后只做一次 isActive 判断。
        val ctx = currentCoroutineContext()
        val saved = try {
            repo.preloadFullTexts(
                limit = limit,
                onProgress = { done, total ->
                    // 回调来自 IO 线程，但 MutableStateFlow 赋值本身是线程安全的。
                    // 对号再写：本轮已经被取消 / 被新一轮顶掉时，别再改状态。
                    if (gen == generation.get()) {
                        _progress.value = _progress.value.copy(done = done, total = total)
                    }
                },
                // 被 cancel() 之后 isActive 变 false，仓库那边会尽快停止派新任务
                shouldStop = { !ctx.isActive }
            )
        } catch (_: Exception) {
            0
        }
        // 收尾也要对号：否则旧轮次会把新一轮刚写上的 running=true 按回 false
        if (gen == generation.get()) {
            _progress.value = _progress.value.copy(running = false, saved = saved)
        }
    }

    private companion object {
        /** 手动「立即缓存全部」一次最多抓多少篇。 */
        const val MANUAL_LIMIT = 300

        /**
         * 自动/后台一次最多抓多少篇。
         *
         * 比手动小得多：后台路径跑在 WorkManager 里，单次任务有 10 分钟上限，
         * 而且用户完全感知不到 —— 少抓一点、分几次抓完更稳妥。
         */
        const val AUTO_LIMIT = 80
    }
}
