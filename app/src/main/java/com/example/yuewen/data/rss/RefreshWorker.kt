package com.example.yuewen.data.rss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.yuewen.MainActivity
import com.example.yuewen.R
import com.example.yuewen.YuewenApplication
import kotlinx.coroutines.flow.first
import androidx.core.app.NotificationCompat

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as YuewenApplication
        val newCount = app.newsRepository.refreshAll()
        // v2.5：`doWork()` 本身就是 suspend 的，直接 `first()` 即可。
        // 以前套了一层 `runBlocking` —— 那会把 WorkManager 的工作线程整个占住，
        // 属于典型的「在挂起函数里做阻塞调用」反模式。
        val notify = app.settingsRepository.notifyFlow.first()
        if (newCount > 0 && notify) showNotification(newCount)
        // v1.9：定时刷新完也顺手把正文缓存一轮。
        // 这里必须 await 完成（runAutoHere 直接跑在当前协程里）——
        // doWork 一返回系统就认为活干完了，扔出去的独立协程会被腰斩。
        // 开关关着 / 不在 Wi-Fi 时它自己会立刻返回，不付出代价。
        runCatching { app.preloader.runAutoHere() }
        return Result.success()
    }

    /**
     * 「有 N 条新内容更新」的通知。
     *
     * ⚠️ v2.7.1 修的坑：**以前根本没设 `contentIntent`** —— 这条通知就是个纯装饰，
     * 用户点它什么都不会发生（Android 12 起，没有 contentIntent 的通知连
     * 「跳转无效」的提示都省了，就是一动不动）。用户反馈的「点通知进不去 App」就是它。
     *
     * 正解是用 `PendingIntent.getActivity()`：让**系统**去启动 Activity。
     * 不要用 `getService()` + 服务里再 `startActivity()` —— 那条路在 Android 10+
     * 会被「后台启动 Activity 限制」拦掉（前台服务也不例外）。
     */
    private fun showNotification(newCount: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "yuewen_news"
        val channel = NotificationChannel(channelId, "新闻更新", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)

        // ⚠️ 这个 PendingIntent **故意不带** `EXTRA_OPEN_LINK`，而且 requestCode 与
        // 朗读通知的错开：本条的语义是「打开 App 首页」，不是「打开某一篇」。
        // 两者若共用一个 PendingIntent（requestCode + filterEquals 相同就会复用），
        // 会出现「点刷新提醒，结果跳进上次朗读的那篇文章」这种串台。
        // 冷启动 / 已在栈顶两种情况都由 MainActivity 兜住（onCreate 与 onNewIntent）。
        val openApp = PendingIntent.getActivity(
            applicationContext,
            REQ_OPEN_APP,
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingFlags()
        )

        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("阅闻")
            .setContentText("有 $newCount 条新内容更新")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        nm.notify(1, notif)
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private companion object {
        /** 与 `TtsPlaybackService.REQ_OPEN_ARTICLE` 分开，避免两个通知共用同一个 PendingIntent。 */
        const val REQ_OPEN_APP = 10
    }
}
