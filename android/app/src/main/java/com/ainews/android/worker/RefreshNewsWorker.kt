package com.ainews.android.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.redrawAllWidgets
import java.util.concurrent.TimeUnit

class RefreshNewsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.refreshNow()
        // Without this the widget kept showing whatever it drew when the app was last open.
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-refresh"

        fun schedule(context: Context, cadenceMinutes: Long = 30) {
            val request = PeriodicWorkRequestBuilder<RefreshNewsWorker>(
                cadenceMinutes.coerceAtLeast(15),
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

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
