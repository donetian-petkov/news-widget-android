package com.ainews.android.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.NewsSchedule
import com.ainews.android.data.ScheduleKind
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.redrawAllWidgets
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs one user-defined schedule at its chosen hour: refresh the feeds, scan the
 * monitors, or build the daily digest.
 */
class ScheduleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.success()
        NewsRepository.initialize(applicationContext)
        NewsRepository.runScheduleNow(scheduleId)
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_SCHEDULE_ID = "schedule-id"

        private fun workName(scheduleId: String) = "ai-news-schedule-$scheduleId"

        fun syncAll(context: Context, schedules: List<NewsSchedule>) {
            schedules.forEach { schedule ->
                if (schedule.enabled) schedule(context, schedule) else cancel(context, schedule.id)
            }
        }

        fun schedule(context: Context, schedule: NewsSchedule) {
            val request = PeriodicWorkRequestBuilder<ScheduleWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis(schedule.hour), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SCHEDULE_ID to schedule.id))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName(schedule.id),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context, scheduleId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(scheduleId))
        }

        fun defaultSchedule(kind: ScheduleKind, hour: Int): NewsSchedule =
            NewsSchedule(
                id = "schedule-${kind.name.lowercase()}-$hour-${System.currentTimeMillis()}",
                kind = kind,
                hour = hour.coerceIn(0, 23),
            )

        private fun initialDelayMillis(hour: Int): Long {
            val now = LocalDateTime.now()
            var target = now.with(LocalTime.of(hour.coerceIn(0, 23), 0))
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis().coerceAtLeast(0)
        }
    }
}
