package com.example.yuewen.data.rss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
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

    private fun showNotification(newCount: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "yuewen_news"
        val channel = NotificationChannel(channelId, "新闻更新", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("阅闻")
            .setContentText("有 $newCount 条新内容更新")
            .setAutoCancel(true)
            .build()
        nm.notify(1, notif)
    }
}
