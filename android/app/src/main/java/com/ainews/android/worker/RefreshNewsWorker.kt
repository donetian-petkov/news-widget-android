package com.ainews.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import java.util.concurrent.TimeUnit

class RefreshNewsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.refreshNow()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshNewsWorker>(
                30,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
