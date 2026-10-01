package com.example.yuewen.data.rss

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val NAME = "yuewen_refresh"

    /** 按设置的分钟数排周期刷新；minutes<=0 时取消。 */
    fun schedule(context: Context, minutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (minutes <= 0) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(
            minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
