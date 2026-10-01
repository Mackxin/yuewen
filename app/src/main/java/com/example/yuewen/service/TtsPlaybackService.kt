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
 * 3. 「回到文章」按钮把 App 拉回前台并打开正在朗读的那一篇。
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
            ACTION_OPEN -> openArticle()
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

    private fun openArticle() {
        val link = tts?.link?.value.orEmpty()
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (link.isNotBlank()) putExtra(EXTRA_OPEN_LINK, link)
        }
        runCatching { startActivity(i) }
        // 双重保险：Activity 已在栈顶时 onNewIntent 也拿得到；这里再往 App 上放一份，
        // MainScreen 订阅到就会打开详情页（走的是和通知按钮同一条路径）。
        (application as? YuewenApplication)?.requestOpenArticle(link)
    }

    private fun buildNotification(st: Status): Notification {
        // 注意：这里只建三个 PendingIntent，全部挂在「通知上的按钮」上。
        // 不需要额外的「点通知本体打开文章」的 activity PendingIntent ——
        // 用 service 的 ACTION_OPEN 更省事：它能同时把 App 拉起来并让 MainScreen 打开那一篇。
        val toggleIntent = PendingIntent.getService(this, 2, intentOf(ACTION_TOGGLE), flags())
        val stopIntent = PendingIntent.getService(this, 3, intentOf(ACTION_STOP), flags())
        val openFromNotif = PendingIntent.getService(this, 4, intentOf(ACTION_OPEN), flags())

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
        const val ACTION_OPEN = "com.example.yuewen.action.TTS_OPEN"

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
