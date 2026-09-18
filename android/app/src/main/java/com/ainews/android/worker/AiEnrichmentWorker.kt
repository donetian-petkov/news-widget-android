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

class AiEnrichmentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NewsRepository.initialize(applicationContext)
        NewsRepository.enrichVisibleStories()
        NewsWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-enrichment"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiEnrichmentWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
