package com.example.yuewen.data.rss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.yuewen.MainActivity
import com.example.yuewen.R
import com.example.yuewen.YuewenApplication
import com.example.yuewen.data.util.DailyDigest
import com.example.yuewen.data.util.DailyDigest.DigestFacts
import kotlinx.coroutines.flow.first

/**
 * 每天到点推一条「今日摘要」（v2.8）。
 *
 * 干了四件事，顺序不能换：
 * 1. **先刷新一轮**。不刷的话「今天 N 条新内容」读的是上次刷新时留下的旧库 ——
 *    夜里没人开 App 的时候，早上 8 点这条摘要是拿七点的库算出来的，数字明显偏小。
 *    刷新本身也就是 RefreshWorker 每 30 分钟在做的事，不多花什么。
 * 2. 取数（今天的条数 / 未读 / 队列 / 今日标题）。
 * 3. 有话才推（[DailyDigest.buildContent] 返回 null 就静默 —— 见那里的注释）。
 * 4. **排下一次**。这一步漏了的话，表现是「摘要只推了一天，之后再也没动静」，
 *    而且因为开关还开着、界面上看不出任何异常，最难查。
 *
 * ⚠️ 这个 Worker 里**没有** `Hilt` / 自定义 `WorkerFactory`：项目用的是
 * `androidx.startup` 的默认自动初始化（和 RefreshWorker 一致），
 * 依赖从 Application 上直接取。
 */
class DailyDigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as YuewenApplication
        val settings = app.settingsRepository

        // 开关可能在「排队期间」被关掉（比如用户昨晚 23:00 关的，任务半夜才排到）——
        // 这里必须重新读一次，不能信排期那一刻的值。
        val enabled = runCatching { settings.digestEnabledFlow.first() }.getOrDefault(false)
        if (!enabled) return Result.success()

        val hour = runCatching { settings.digestHourFlow.first() }.getOrDefault(DailyDigest.DEFAULT_HOUR)
        val minute = runCatching { settings.digestMinuteFlow.first() }.getOrDefault(DailyDigest.DEFAULT_MINUTE)

        // ① 先把库刷到今天的样子（失败就沿用旧库，不阻断摘要）
        runCatching { app.newsRepository.refreshAll() }

        // ② 取数
        val today = DailyDigest.todayStart(System.currentTimeMillis())
        val facts: DigestFacts = runCatching { app.newsRepository.digestFacts(today) }
            .getOrElse { DigestFacts(0, 0, 0, emptyList()) }

        // ③ 推
        val content = DailyDigest.buildContent(facts)
        if (content != null && canPostNotifications()) {
            runCatching { showNotification(content) }
        }

        // ④ 排明天那一班
        //
        // ⚠️ 这里**必须**用 replace（REPLACE）而不是 ensure（KEEP）：
        // 此刻「自己」还占着 yuewen_daily_digest 这个名字（状态是 RUNNING），
        // KEEP 会把它判成「已经有一条在等了」而直接把入队丢掉 ——
        // 表现就是摘要只推一天。REPLACE 会把这一代（也就是正在收尾的我们）顶掉、
        // 排上新一代；因为通知在上一行已经发完了，被顶掉没有任何副作用。
        runCatching { DailyDigestScheduler.replace(applicationContext, true, hour, minute) }

        return Result.success()
    }

    /**
     * Android 13 起通知是运行时权限，没授权时 `notify()` 会被**静默丢弃**。
     *
     * 自己判一下而不是直接调：这样「摘要没出来」至少能在日志里和「没有可推的内容」
     * 区分开 —— 否则两种情况在外部表现完全一样（都是什么都没发生）。
     */
    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 摘要通知。
     *
     * 频道 / 通知 id / requestCode 三样都必须和现有通知错开，否则会互相顶掉：
     * - 频道：`yuewen_news`（刷新提醒）/ `yuewen_tts_playback`（朗读）已占，这里用第三个；
     * - 通知 id：刷新用 1、朗读用 8801，摘要用 2；
     * - requestCode：刷新用 10、朗读用 2/3/21，摘要用 20。
     *   与刷新那条**必须**分开 —— 两者的 `Intent` 都是「打开 MainActivity」，
     *   requestCode + filterEquals 相同的话 `PendingIntent` 会被复用，
     *   于是「点摘要结果跳进了上次朗读的那篇」这种串台就出现了。
     */
    private fun showNotification(content: DailyDigest.DigestContent) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 只在频道不存在时创建：重复创建虽然不会报错，但会覆盖用户
        // 在这个频道上做过的个性化（改提示音 / 关横幅），属于静默冒犯。
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日摘要", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        // 点通知 = 打开 App 首页。刻意不带 EXTRA_OPEN_LINK：
        // 摘要是「今天大概有什么」，具体读哪篇由用户进来自己挑，
        // 替他决定「打开第三条」反而容易让他以为 App 猜错了。
        val openApp = PendingIntent.getActivity(
            applicationContext,
            REQ_OPEN_APP,
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingFlags()
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            // 展开态：把今天最新的几条标题列出来。
            // 用 BigTextStyle 而不是 InboxStyle —— 标题长短差别很大，
            // Inbox 每行会被裁掉，BigText 会自动换行，中文标题下差别很明显。
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private companion object {
        const val CHANNEL_ID = "yuewen_daily_digest"

        /** 与 `RefreshWorker`（1）和 `TtsPlaybackService`（8801）错开。 */
        const val NOTIFICATION_ID = 2

        /** 与 `RefreshWorker.REQ_OPEN_APP`（10）、`TtsPlaybackService.REQ_OPEN_ARTICLE`（21）错开。 */
        const val REQ_OPEN_APP = 20
    }
}
