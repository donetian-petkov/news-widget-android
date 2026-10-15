package com.ainews.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * A minute past every hour, writes the briefing for the hour that just ended, like the
 * Mac widget's backend does. Hours that are already written or had no stories are skipped.
 */
class BriefingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.writeScheduledHourlyBriefing()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-hourly-briefing"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BriefingWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Until a minute past the next hour, so stories fetched at :00 make it in. */
        private fun initialDelayMillis(): Long {
            val now = LocalDateTime.now()
            val target = now.withMinute(1).withSecond(0).withNano(0).let { if (it.isAfter(now)) it else it.plusHours(1) }
            return Duration.between(now, target).toMillis().coerceAtLeast(0)
        }
    }
}
