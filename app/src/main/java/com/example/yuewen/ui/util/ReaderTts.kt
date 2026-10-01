package com.example.yuewen.ui.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 一段待朗读文本。
 *
 * [para] 是它在「标题(0) + 正文文本段(1..n)」这个朗读序列里的位置，
 * 详情页靠它把「正在念第几段」翻译成「正文滚到哪一行」。
 */
data class TtsPiece(val para: Int, val text: String)

/**
 * 朗读语速档位（v1.9）。
 *
 * 存的是**档位序号**而不是浮点数：以后想微调具体倍率时（比如把「快」从 1.25 调到 1.3），
 * 老用户的选择仍然落在「快」这一档，不会莫名其妙跑偏。
 * 1.0 是系统默认语速。
 */
val TtsRates: List<Float> = listOf(0.8f, 1.0f, 1.25f, 1.5f)

/** 和 [TtsRates] 一一对应的中文标签。 */
val TtsRateLabels: List<String> = listOf("慢", "标准", "快", "很快")

/** 普通片段的 utteranceId 前缀。 */
private const val TAG = "yuewen_tts_"

/** 最后一片的 utteranceId 前缀 —— 只有它读完才算整篇读完。 */
private const val TAG_LAST = "yuewen_tts_last_"

/**
 * 全 App 共用的朗读控制台（v1.8 从详情页里搬出来）。
 *
 * **为什么不能让它跟着详情页活**：以前的引擎是在 `DetailScreen` 的 `DisposableEffect`
 * 里创建、`onDispose` 里 `shutdown()`。于是「朗读中点一下底部导航想边听边逛」
 * 会顺手把引擎关掉 —— 声音断、而且因为详情浮层还盖在最上面，
 * 看起来就像「点了底栏毫无反应」。用户真正的诉求是：
 * *我玩我的，你后台继续念*。
 *
 * 现在引擎挂在 [com.example.yuewen.YuewenApplication] 上，生命周期 = 进程生命周期；
 * 详情页只是「订阅者」，退出详情页只是不再画高亮，声音照念；
 * 想停就再回到那篇文章点一下停止（或让 TTS 自己读完）。
 *
 * **v2.0 新增暂停 / 继续**：系统 `TextToSpeech` 压根没有 pause API
 * （只有 `stop()`，一停队列就清空）。所以这里自己做：
 * 记住「正在念第几片」→ [pause] 时停掉并记下位置 → [resume] 时
 * 把剩余片段重新提交一遍。对用户来说体验和真暂停一样，只是当前那句会从头重念。
 *
 * 线程说明：`UtteranceProgressListener` 的回调跑在 TTS 服务线程上，
 * 而 `MutableStateFlow.value` 的赋值是线程安全的，所以这里不需要再往主线程 post。
 */
class ReaderTts(context: Context) {

    /** 只用 Application Context：引擎活得比任何界面都久，别把 Activity 拖住。 */
    private val appContext: Context = context.applicationContext

    /**
     * TTS 引擎实例。
     *
     * v2.3：加上了 `@Volatile`。它既在主线程写（[ensureEngine] 里 `engine = created`），
     * 又在 TTS 初始化回调线程、`UtteranceProgressListener` 回调线程里被读
     * （[pause] / [stop] / 重发片段时都要拿它）。同文件里 `ready` / `queue` /
     * `interruptUntil` 早就标了 `@Volatile`，唯独被访问最多的这个漏了 ——
     * 弱内存序下回调线程可能读到陈旧的 null，症状是「已经开始念了，但暂停/停止按不动」。
     */
    @Volatile
    private var engine: TextToSpeech? = null
    private var initializing = false

    /** 引擎是否已就绪（系统装了可用的中文语音包）。跨线程读，用 @Volatile。 */
    @Volatile
    var ready: Boolean = false
        private set

    /**
     * 当前语速倍数（1.0 = 系统默认）。
     *
     * 记在这里而不是每次 speak 都从 DataStore 读：`speak()` 是同步方法，
     * 详情页在偏好变化时调一次 [setRate] 就够了；引擎重建时也用它把语速补回去。
     */
    @Volatile
    var rate: Float = 1.0f
        private set

    /**
     * 「主动中断」时间窗。
     *
     * `engine.stop()` 之后有些 ROM 会回调 `onError`，而我们的 onError 是「把朗读状态清干净」。
     * 那样一暂停就会立刻变成「没在朗读」——按钮都消失了，还怎么继续。
     * 用一个几百毫秒的窗口把这批「自己造成的」错误吞掉。
     */
    @Volatile
    private var interruptUntil: Long = 0L

    /** 本次朗读的完整片段队列（暂停续读要靠它重发剩余片段）。 */
    @Volatile
    private var queue: List<TtsPiece> = emptyList()

    private val _speaking = MutableStateFlow(false)

    /** 是否处于「朗读中」（**含暂停**：暂停时依然算有朗读任务，通知栏按钮才在）。 */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /** 是否暂停。 */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _para = MutableStateFlow(-1)

    /** 当前正在读的段落号（-1 = 没在朗读），驱动正文自动滚动 + 高亮。暂停时保留。 */
    val para: StateFlow<Int> = _para.asStateFlow()

    private val _link = MutableStateFlow<String?>(null)

    /** 正在朗读哪篇文章（文章 link）。null = 没有朗读任务。 */
    val link: StateFlow<String?> = _link.asStateFlow()

    private val _title = MutableStateFlow("")

    /** 正在朗读的文章标题（通知栏 / 悬浮条显示用）。 */
    val title: StateFlow<String> = _title.asStateFlow()

    private val _pieceIndex = MutableStateFlow(0)

    /** 当前念到第几片（0 起）。暂停续读的锚点。 */
    val pieceIndex: StateFlow<Int> = _pieceIndex.asStateFlow()

    private val _pieceCount = MutableStateFlow(0)

    /** 本次朗读一共有多少片。配合 [pieceIndex] 做「读到 37%」这种进度提示。 */
    val pieceCount: StateFlow<Int> = _pieceCount.asStateFlow()

    private val listener = object : UtteranceProgressListener() {
        // utteranceId 形如 "yuewen_tts_<para>|<i>" 或 "yuewen_tts_last_<para>|<i>"
        override fun onStart(utteranceId: String?) {
            val payload = utteranceId
                ?.removePrefix(TAG_LAST)
                ?.removePrefix(TAG)
                ?: return
            val para = payload.substringBefore('|').toIntOrNull()
            val idx = payload.substringAfter('|', "").toIntOrNull()
            if (para != null) _para.value = para
            if (idx != null) _pieceIndex.value = idx
        }

        override fun onDone(utteranceId: String?) {
            // 中间片段读完不理会，只有最后一片读完才把「朗读中」关掉
            if (utteranceId?.startsWith(TAG_LAST) == true) {
                clearState()
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            if (inInterruptWindow()) return
            clearState()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (inInterruptWindow()) return
            clearState()
        }
    }

    /**
     * 预热引擎。进详情页时调一次，用户真正点「朗读」时通常已经就绪，
     * 不用再听「引擎还在准备中」。
     */
    fun warmUp() = ensureEngine()

    /**
     * 设置语速（v1.9）。
     *
     * 引擎还没建好时只记下来，[ensureEngine] 建好之后会补设一次；
     * 正在朗读时改，从**下一句**开始生效（TTS 引擎本身没有「变速重播当前句」的能力）。
     */
    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2.0f)
        runCatching { engine?.setSpeechRate(rate) }
    }

    /**
     * 开始朗读。
     *
     * @param title 文章标题，只用于通知栏 / 悬浮条显示
     * @return false 表示引擎还没准备好（或没有内容），调用方负责提示用户
     */
    fun speak(pieces: List<TtsPiece>, link: String, title: String = ""): Boolean {
        ensureEngine()
        val e = engine
        if (e == null || !ready || pieces.isEmpty()) return false

        // 每次朗读前都补一次语速：用户可能在两篇文章之间改过设置
        runCatching { e.setSpeechRate(rate) }

        queue = pieces
        _title.value = title
        _link.value = link
        _para.value = -1
        _paused.value = false
        _speaking.value = true
        _pieceCount.value = pieces.size
        _pieceIndex.value = 0

        // 第一片 QUEUE_FLUSH 清掉残留队列，其余排队接上；长文才不会念一半就断。
        enqueueFrom(0)
        return true
    }

    /**
     * 暂停（v2.0）。
     *
     * `speaking` **刻意保持 true** —— 暂停之后用户还要能「继续」，
     * 通知栏的播放/暂停按钮也要还在。是不是暂停看 [paused]。
     */
    fun pause() {
        if (!_speaking.value || _paused.value) return
        beginInterruptWindow()
        runCatching { engine?.stop() }
        _paused.value = true
    }

    /** 继续（从暂停处的那一片重新念）。 */
    fun resume() {
        if (!_speaking.value || !_paused.value) return
        if (queue.isEmpty()) {
            stop()
            return
        }
        if (!ready) {
            // 引擎被系统回收了：重发也发不出去，直接收摊，别让界面停在假状态
            ensureEngine()
            stop()
            return
        }
        _paused.value = false
        enqueueFrom(_pieceIndex.value.coerceIn(0, queue.lastIndex))
    }

    /** 播放 / 暂停切换（通知栏按钮用）。 */
    fun toggle() {
        if (_paused.value) resume() else pause()
    }

    /** 停止朗读并清空状态。 */
    fun stop() {
        beginInterruptWindow()
        runCatching { engine?.stop() }
        clearState()
    }

    /** 跳到第 [index] 片继续念（通知栏「上一句 / 下一句」留给后续版本用）。 */
    fun seekTo(index: Int) {
        if (queue.isEmpty()) return
        val i = index.coerceIn(0, queue.lastIndex)
        if (!ready) return
        beginInterruptWindow()
        runCatching { engine?.stop() }
        _paused.value = false
        _speaking.value = true
        _pieceIndex.value = i
        enqueueFrom(i)
    }

    // ------------------------------------------------------------------ 内部

    private fun enqueueFrom(from: Int) {
        val e = engine ?: return
        val list = queue
        if (from !in list.indices) {
            clearState()
            return
        }
        for (i in from until list.size) {
            val piece = list[i]
            val id = if (i == list.lastIndex) {
                "$TAG_LAST${piece.para}|$i"
            } else {
                "$TAG${piece.para}|$i"
            }
            e.speak(
                piece.text,
                if (i == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                id
            )
        }
    }

    private fun clearState() {
        _speaking.value = false
        _paused.value = false
        _para.value = -1
        _link.value = null
        _pieceIndex.value = 0
        _pieceCount.value = 0
        queue = emptyList()
    }

    private fun beginInterruptWindow() {
        interruptUntil = System.currentTimeMillis() + 800L
    }

    private fun inInterruptWindow(): Boolean = System.currentTimeMillis() < interruptUntil

    /**
     * 惰性建引擎：只有真的要用语音才付出这份开销（首帧启动不受影响）。
     *
     * `initializing` 兼作失败重试开关。⚠️ v2.3 修过一个坑：
     * 以前失败分支只把 `initializing` 置回 false，**没有清掉 `engine`**，
     * 而 `engine` 在外层已经被赋成刚建好（但初始化失败）的那个实例。
     * 于是下次 `ensureEngine()` 进来看见 `engine != null` 直接 return ——
     * 「重试」永远不会发生。TTS 引擎偶发初始化失败（典型是刚开机语音服务还没就绪）后，
     * 整个进程生命周期内朗读再也起不来，用户只能重启 App。
     * 现在失败时把半成品实例 shutdown 掉并置空，下次点朗读真的会重新建一个。
     */
    private fun ensureEngine() {
        if (engine != null || initializing) return
        initializing = true

        // 局部别名：onInit 回调是异步的，用同一个局部引用才拿得到刚建好的那个引擎
        var created: TextToSpeech? = null
        created = TextToSpeech(appContext) { status ->
            val e = created
            if (status != TextToSpeech.SUCCESS) {
                // 建不起来：把半成品丢掉 + 放开开关，下次点朗读会重新建一个
                runCatching { e?.shutdown() }
                if (engine === e) engine = null
                initializing = false
                ready = false
            } else if (e != null) {
                // 中文内容优先用中文引擎；系统没有中文语音包时退回默认语言，
                // 读音会不准但至少有声音，比点了没反应友好。
                val want = if (Locale.getDefault().language.startsWith("zh")) Locale.CHINA else Locale.getDefault()
                val r = runCatching { e.setLanguage(want) }.getOrNull()
                var ok = r != null &&
                        r != TextToSpeech.LANG_MISSING_DATA &&
                        r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ok) {
                    runCatching { e.setLanguage(Locale.getDefault()) }
                    ok = true
                }
                ready = ok
                runCatching { e.setSpeechRate(rate) }
                runCatching { e.setOnUtteranceProgressListener(listener) }
            }
        }
        engine = created
    }
}
