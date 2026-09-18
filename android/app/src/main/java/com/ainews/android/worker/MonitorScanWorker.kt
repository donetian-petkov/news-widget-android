package com.ainews.android.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.NewsWidget
import java.util.concurrent.TimeUnit

class MonitorScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.scanMonitorsNow()
        NewsWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-monitor-scan"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorScanWorker>(
                24,
                TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
