package com.example.yuewen

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.yuewen.data.SettingsRepository
import com.example.yuewen.data.db.AppDatabase
import com.example.yuewen.data.net.Http
import com.example.yuewen.data.preload.PreloadManager
import com.example.yuewen.data.repository.NewsRepository
import com.example.yuewen.data.rss.RefreshScheduler
import com.example.yuewen.service.TtsPlaybackService
import com.example.yuewen.ui.util.ReaderTts
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yuewen_settings")

class YuewenApplication : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
        private set
    lateinit var newsRepository: NewsRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    /**
     * 全 App 唯一的朗读控制台（v1.8）。
     *
     * 挂在 Application 上而不是详情页里：这样「读到一半切到别的 Tab」声音不会断，
     * 用户才能边听边逛（详情页只负责显示高亮，不再负责引擎生死）。
     * 用 `by lazy` 惰性建，没用过语音的用户不付出这份开销。
     */
    val tts: ReaderTts by lazy { ReaderTts(this) }

    /**
     * 离线预加载调度器（v1.9）。
     *
     * 同样是 App 级单例：手动触发（设置页按钮）、自动触发（刷新完跑一轮）、
     * 进度展示（设置页进度条）三处必须看到同一份状态，散进 ViewModel 就对不上了。
     * 惰性创建，没开这个功能的用户不付出开销。
     */
    val preloader: PreloadManager by lazy {
        PreloadManager(newsRepository, settingsRepository, this)
    }

    /**
     * 「回到朗读中的文章」（v2.0）。
     *
     * 朗读在后台继续念时，用户切去别的 Tab 办事，想回到那篇文章得重新找 ——
     * 很烦。通知栏的「回到文章」、以及 App 内的「正在朗读」悬浮条都往这里塞一个 link，
     * MainScreen 订阅到就把它打开。用完置回 null，避免反复弹同一个页面。
     */
    private val _pendingOpenLink = MutableStateFlow<String?>(null)
    val pendingOpenLink: StateFlow<String?> = _pendingOpenLink.asStateFlow()

    fun requestOpenArticle(link: String?) {
        if (!link.isNullOrBlank()) _pendingOpenLink.value = link
    }

    fun consumeOpenArticle() {
        _pendingOpenLink.value = null
    }

    // 后台协程里吞不掉的异常，记到 debug 日志，避免直接干掉进程
    private val eh = CoroutineExceptionHandler { _, e -> logDebug(e) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + eh)

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()

        try {
            database = Room.databaseBuilder(this, AppDatabase::class.java, "yuewen.db")
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5
                )
                // ⚠️ v2.3：这里以前是 `fallbackToDestructiveMigration()`。
                // 那个兜底的意思是「只要迁移失败就把所有表 drop 掉重建」——
                // 对一个把「收藏 / 笔记」当核心价值的阅读器来说，
                // 这是**静默清空用户全部数据**，而且不抛异常、崩溃日志里也留不下痕迹。
                //
                // 现在只保留「降级」这一种真正无解的兜底：用户装了旧版本覆盖新版本时，
                // 库版本比 App 认识的还高，schema 对不上也没别的办法。
                // 升级方向的迁移失败会正常抛异常（能在错误页看到日志），
                // 这是**故意**的 —— 宁可报错让我们发现，也不能悄悄把数据删了。
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        } catch (e: Exception) {
            logDebug(e)
        }

        settingsRepository = SettingsRepository(dataStore)

        try {
            if (::database.isInitialized) {
                newsRepository = NewsRepository(
                    database.articleDao(),
                    database.noteDao(),
                    settingsRepository,
                    this
                )
            }
        } catch (e: Exception) {
            logDebug(e)
        }

        // WorkManager 调度失败不能拖垮启动
        try {
            RefreshScheduler.schedule(this, 30)
        } catch (e: Exception) {
            logDebug(e)
        }

        // 首次填充默认源；真正的「启动刷新」由 HomeViewModel 负责
        // （这样能复用下拉刷新的转圈提示，也方便在设置里关掉 + 做节流）
        scope.launch {
            try {
                settingsRepository.ensureSeeded()
                // 按用户设置的频率排期（以前固定写死 30 分钟，设置里选「关闭」也不生效）
                RefreshScheduler.schedule(this@YuewenApplication, settingsRepository.refreshMinutesFlow.first())
            } catch (e: Exception) {
                logDebug(e)
            }
        }

        // 朗读 → 前台服务（通知栏 / 锁屏控制条）的开关联动。
        // 把「什么时候挂通知」这件事放在 Application 上，而不是散进详情页：
        // 朗读可能在任意页面开始，也只有在这里能同时看到「是否在念」和「用户是否要通知」。
        scope.launch {
            try {
                combine(tts.speaking, settingsRepository.ttsNotifyFlow) { speaking, notify ->
                    speaking && notify
                }.distinctUntilChanged().collect { want ->
                    if (want) TtsPlaybackService.start(this@YuewenApplication)
                    else TtsPlaybackService.stop(this@YuewenApplication)
                }
            } catch (e: Exception) {
                logDebug(e)
            }
        }
    }

    /**
     * 图片加载器（Coil 会自动使用这里返回的实例）。
     *
     * 为什么要自定义：Coil 默认的 User-Agent 是 "Coil/2.x"，
     * 不少图床 / CDN 会把这个 UA 当成爬虫直接 403 —— 表现就是列表和正文里
     * 「图片位置一片空白」。这里统一换成移动浏览器 UA，成功率明显更高。
     * 顺带复用全局 OkHttp（共享连接池），并开启淡入动画。
     */
    override fun newImageLoader(): ImageLoader {
        val imageClient = Http.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                val fixed = if (req.header("User-Agent").isNullOrBlank()) {
                    req.newBuilder().header("User-Agent", Http.UA_IMAGE).build()
                } else {
                    req
                }
                chain.proceed(fixed)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(250)
            .build()
    }

    private fun installCrashHandler() {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(throwable)
            def?.uncaughtException(thread, throwable)
        }
    }

    private fun writeLog(name: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val device = "机型=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} 安卓API=${android.os.Build.VERSION.SDK_INT}"
            val f = File(filesDir, name)
            // 防止日志无限增长：超过 200KB 就裁掉前一半
            if (f.exists() && f.length() > 200_000) {
                val keep = f.readText().takeLast(100_000)
                f.writeText(keep)
            }
            f.appendText("==== ${System.currentTimeMillis()} | $device ====\n$sw\n\n")
        } catch (_: Exception) {
        }
    }

    private fun logDebug(t: Throwable) = writeLog("yuewen_debug.log", t)
    private fun logCrash(t: Throwable) = writeLog("yuewen_crash.log", t)

    override fun onTerminate() {
        scope.cancel()
        super.onTerminate()
    }
}
