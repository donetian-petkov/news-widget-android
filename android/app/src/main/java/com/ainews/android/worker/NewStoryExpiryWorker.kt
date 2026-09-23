package com.ainews.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.redrawAllWidgets
import java.util.concurrent.TimeUnit

/**
 * Takes the NEW badges off a few minutes after the reader has had a chance to see them.
 *
 * Android never tells an app that its widget is on screen - the launcher draws it and says
 * nothing - so the nearest honest signal is the phone being woken and unlocked, which is
 * when someone is looking at their home screen. This is started then, and when it runs it
 * clears the badge on every story that was already there at that moment. Anything that
 * arrives in the meantime keeps its badge for the next look.
 */
class NewStoryExpiryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val seenAt = inputData.getLong(KEY_SEEN_AT, 0L)
        if (seenAt <= 0L) return Result.success()
        NewsRepository.initialize(applicationContext)
        NewsRepository.clearNewMarkersSeenBefore(seenAt)
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "ai-news-new-badge-expiry"
        private const val KEY_SEEN_AT = "seen_at"

        /** How long the badges stay up once the reader is at their home screen. */
        val LOOK_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)

        /**
         * Started on every unlock, but with KEEP: a second unlock inside the window must not
         * push the deadline back, or badges would never go while the phone is in use.
         */
        fun scheduleAfterLook(context: Context, seenAt: Long = System.currentTimeMillis()) {
            val request = OneTimeWorkRequestBuilder<NewStoryExpiryWorker>()
                .setInitialDelay(LOOK_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SEEN_AT to seenAt))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
