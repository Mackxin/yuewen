package com.example.yuewen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.yuewen.MainActivity
import com.example.yuewen.R
import com.example.yuewen.YuewenApplication
import com.example.yuewen.ui.util.ReaderTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 朗读的前台服务 + 锁屏 / 通知栏控制（v2.0）。
 *
 * 为什么必须是**前台服务**：Android 8 起，App 退到后台后普通后台服务随时会被回收，
 * 通知也可能被延迟；而朗读是「用户明确在听、还指望它一直念下去」的场景，
 * 用前台服务才是系统的正确用法（持续的、用户可感知的任务）。
 *
 * 服务本身**不碰 TTS 引擎** —— 引擎在 [YuewenApplication.tts] 上，生命周期等于进程。
 * 这里只做三件事：
 * 1. 挂一个常驻通知（进度 = 第几片 / 共几片，看起来就像个播放器）；
 * 2. 把通知上的按钮接到引擎的 pause / resume / stop；
 * 3. 「回到文章」（以及点通知本体）把 App 拉回前台并打开正在朗读的那一篇。
 *
 * ⚠️ 第 3 条**不能**由本服务自己 `startActivity()` —— Android 10 起后台启动 Activity 会被拦，
 * 必须交给 `PendingIntent.getActivity()` 让系统去拉起。详见 [openArticleIntent]。
 *
 * 稳妥起见，[startForeground] 全部包在 runCatching 里：
 * 个别 ROM 会因为通知权限被关、或不允许该 FGS 类型而抛异常，
 * 那种情况下**宁可没有通知，也不能让 App 崩**（朗读本身由引擎负责，照常出声）。
 */
class TtsPlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    private val tts: ReaderTts?
        get() = (application as? YuewenApplication)?.tts

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // 订阅引擎状态：暂停 / 继续 / 换篇 / 读完，通知都要跟着变
        watcher = scope.launch {
            val t = tts ?: return@launch
            combine(t.speaking, t.paused, t.title, t.pieceIndex, t.pieceCount) { speaking, paused, title, idx, count ->
                Status(speaking, paused, title, idx, count)
            }.collect { st ->
                if (!st.speaking) {
                    // 念完了（或被停了）：撤掉前台，结束服务
                    runCatching { stopForegroundCompat() }
                    stopSelf()
                    return@collect
                }
                runCatching { startForegroundCompat(buildNotification(st)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> tts?.toggle()
            ACTION_STOP -> tts?.stop()
            else -> {
                // 单纯被拉起：立刻挂上通知，避免「5 秒内没 startForeground」被杀
                val t = tts ?: return START_NOT_STICKY
                val st = Status(
                    speaking = true,
                    paused = t.paused.value,
                    title = t.title.value,
                    index = t.pieceIndex.value,
                    count = t.pieceCount.value
                )
                runCatching { startForegroundCompat(buildNotification(st)) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 内部

    private data class Status(
        val speaking: Boolean,
        val paused: Boolean,
        val title: String,
        val index: Int,
        val count: Int
    )

    /**
     * ⚠️ v2.7.1 修的坑：**点通知进不去 App**。
     *
     * 原来的做法是 contentIntent（点通知本体）和「回到文章」按钮都指向本服务的
     * `ACTION_OPEN`，再由服务里 `startActivity()` 把 MainActivity 拉起来。
     * 这条路在 **Android 10（API 29）起会被系统直接拦掉** ——
     * 后台启动 Activity 有限制，前台服务也不在豁免名单里（那是留给闹钟、来电之类场景的口子）。
     * 表现就是：点通知毫无反应，App 一动不动，日志里连个异常都没有。
     *
     * 正解：**别自己启动 Activity，让系统去启动** —— 用 `PendingIntent.getActivity()`。
     * 从通知栏点击属于「用户主动交互」，系统会正常把 Activity 带到前台，不受那条限制。
     *
     * 链接通过 `EXTRA_OPEN_LINK` 传给 MainActivity：
     * - Activity 还活着 → `onNewIntent` 接住（`singleTop` + `FLAG_ACTIVITY_CLEAR_TOP`）；
     * - 进程被杀后冷启动 → `onCreate` 接住。
     * 两条路都丢给 `YuewenApplication.requestOpenArticle()`，MainScreen 订阅到就打开那一篇。
     * 于是 App 里**不需要**再留一条「服务转发」的备用路径。
     */
    private fun openArticleIntent(): PendingIntent {
        val link = tts?.link?.value.orEmpty()
        return PendingIntent.getActivity(
            this,
            REQ_OPEN_ARTICLE,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (link.isNotBlank()) putExtra(EXTRA_OPEN_LINK, link)
            },
            flags()
        )
    }

    private fun buildNotification(st: Status): Notification {
        // 三个 PendingIntent：两个挂 service（暂停/继续、停止），一个挂 activity（打开文章）。
        val toggleIntent = PendingIntent.getService(this, 2, intentOf(ACTION_TOGGLE), flags())
        val stopIntent = PendingIntent.getService(this, 3, intentOf(ACTION_STOP), flags())
        // 点通知本体 与 「回到文章」按钮 复用同一个 —— 两处语义完全一样，
        // 用两个的话还得让 requestCode 区别开，没意义。
        val openFromNotif = openArticleIntent()

        val progress = if (st.count > 0) {
            "（${((st.index + 1).coerceAtMost(st.count) * 100) / st.count}%）"
        } else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(st.title.ifBlank { "正在朗读" })
            .setContentText(if (st.paused) "已暂停$progress" else "正在朗读$progress")
            .setContentIntent(openFromNotif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // 锁屏可见：默认通知在锁屏上是隐藏内容的，朗读控制必须露出来才有意义
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (st.paused) R.drawable.ic_stat_play else R.drawable.ic_stat_pause,
                if (st.paused) "继续" else "暂停",
                toggleIntent
            )
            .addAction(R.drawable.ic_stat_stop, "停止", stopIntent)
            .addAction(R.drawable.ic_stat_read, "回到文章", openFromNotif)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun intentOf(action: String) =
        Intent(this, TtsPlaybackService::class.java).setAction(action)

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "文章朗读", NotificationManager.IMPORTANCE_LOW).apply {
            description = "朗读文章时的控制条：暂停、停止、回到文章"
            setShowBadge(false)
            enableVibration(false)
        }
        runCatching { nm.createNotificationChannel(ch) }
    }

    companion object {
        const val CHANNEL_ID = "yuewen_tts_playback"
        const val NOTIF_ID = 8801
        const val EXTRA_OPEN_LINK = "yuewen_open_link"

        const val ACTION_TOGGLE = "com.example.yuewen.action.TTS_TOGGLE"
        const val ACTION_STOP = "com.example.yuewen.action.TTS_STOP"

        /**
         * 「打开朗读中的文章」那个 activity PendingIntent 的请求码。
         *
         * ⚠️ 别用 4 —— v2.7.0 及以前这里是个 `getService(…, 4, ACTION_OPEN)`，
         * 老版本残留的 PendingIntent 在个别 ROM 上还会被复用。换个数最省事。
         * 同时也要和 `RefreshWorker` 的 `REQ_OPEN_APP`（10）错开。
         */
        private const val REQ_OPEN_ARTICLE = 21

        /** 拉起前台服务（朗读开始时调）。失败安静吞掉，不影响出声。 */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TtsPlaybackService::class.java)
                )
            }
        }

        /** 停掉服务（朗读结束时调）。 */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TtsPlaybackService::class.java)) }
        }
    }
}
