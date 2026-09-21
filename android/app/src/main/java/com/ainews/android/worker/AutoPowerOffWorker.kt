package com.ainews.android.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.redrawAllWidgets
import java.util.concurrent.TimeUnit

class AutoPowerOffWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.powerOffFromTimeout()
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-auto-power-off"

        fun schedule(context: Context, autoPowerOffAt: Long) {
            val delay = (autoPowerOffAt - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<AutoPowerOffWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
