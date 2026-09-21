package com.ainews.android.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import com.ainews.android.notifications.AlertNotifier
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.redrawAllWidgets
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class MonitorScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.scanMonitorsNow()
        AlertNotifier(applicationContext).notifyMatches(
            matches = NewsRepository.state.value.alertMatches,
            stories = NewsRepository.state.value.stories,
        )
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-monitor-scan"

        fun schedule(context: Context, scanHour: Int = 20) {
            val request = PeriodicWorkRequestBuilder<MonitorScanWorker>(
                24,
                TimeUnit.HOURS,
            )
                .setInitialDelay(initialDelayMillis(scanHour), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun initialDelayMillis(scanHour: Int): Long {
            val now = LocalDateTime.now()
            val targetTime = LocalTime.of(scanHour.coerceIn(0, 23), 0)
            var target = now.with(targetTime)
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis().coerceAtLeast(0)
        }
    }
}
