package com.ainews.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.redrawAllWidgets
import java.util.concurrent.TimeUnit

/** Switches AI off once its session is up, so a forgotten switch cannot run up a bill. */
class AiAutoOffWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.stopAiFromTimeout()
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-ai-auto-off"

        fun schedule(context: Context, stopAt: Long) {
            val request = OneTimeWorkRequestBuilder<AiAutoOffWorker>()
                .setInitialDelay((stopAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
