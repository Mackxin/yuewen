package com.example.yuewen.data.rss

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.yuewen.data.util.DailyDigest
import java.util.concurrent.TimeUnit

/**
 * 每日摘要的排期（v2.8）。
 *
 * ## 为什么是「一次性任务 + 自己排下一次」而不是周期任务
 *
 * 周期任务（`PeriodicWorkRequest`）的**下一次触发时刻是相对上一次执行算的**，
 * 系统每次延迟个几分钟就会累积下去 —— 设了 8 点，一个月后能漂到 9 点半，
 * 而且用户完全看不出为什么。
 *
 * 一次性任务每次跑完都用「当下的真实时间」重算到明天的 hh:mm，
 * 每天都是准的，没有任何累积误差。代价是要自己排下一次（见 [DailyDigestWorker] 的最后一步）。
 *
 * ## ensure 和 replace 为什么必须分开
 *
 * 这是本文件唯一一个容易踩死的地方：
 *
 * [ensure] 用 `KEEP`，给「App 冷启动 / 设置变化」用 —— **已经排上就不动它**。
 * 如果这里用 `REPLACE`，会撞上这个场景：WorkManager 的定时任务**不准时**，
 * 8:00 那班被系统压到 9:30 才跑；用户 8:15 打开了 App，冷启动 → `REPLACE` →
 * 把今天这一班顶成了明天的 8:00 —— **今天的摘要永远收不到**。
 * 而用户看到的只是「设了 8 点，什么都没收到」，根本无从排查。
 *
 * [replace] 用 `REPLACE`，只在**用户主动改了开关或时间**时调（见 `SettingsViewModel`）。
 * `enqueueUniqueWork` 的入队操作在 WorkManager 内部是串行的，
 * 所以「设置页 replace → Application 的订阅紧接着 ensure(KEEP)」这个顺序不会打架：
 * KEEP 那一下会看到已经有一条在等，直接跳过。
 */
object DailyDigestScheduler {

    /** 唯一任务名，也是 tag（排查时 `adb shell dumpsys jobscheduler | grep yuewen_daily_digest` 用得上）。 */
    const val NAME = "yuewen_daily_digest"

    /**
     * 「保证这件事被排上了」——已排上就什么都不做。
     *
     * 用在两处：App 冷启动时（首次开关 / 被系统清过任务时补排），
     * 以及设置变化后由 Application 的订阅顺手确认一下。
     */
    fun ensure(context: Context, enabled: Boolean, hour: Int, minute: Int) =
        enqueue(context, enabled, hour, minute, force = false)

    /** 「按新的设置重排」——把旧的顶掉。只在用户**主动改**开关 / 时间时调。 */
    fun replace(context: Context, enabled: Boolean, hour: Int, minute: Int) =
        enqueue(context, enabled, hour, minute, force = true)

    private fun enqueue(context: Context, enabled: Boolean, hour: Int, minute: Int, force: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            // 关掉时用 cancel 而不是「排一个什么都不干的」：
            // WorkManager 里躺着一条永远不干活的待办，是所有「开关关了却还在跑」类 bug 的温床。
            runCatching { wm.cancelUniqueWork(NAME) }
            return
        }

        val delay = DailyDigest.nextDelayMillis(System.currentTimeMillis(), hour, minute)
        val req = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(NAME)
            .build()
        runCatching {
            wm.enqueueUniqueWork(
                NAME,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                req
            )
        }
    }
}
