package com.ainews.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ainews.android.MainActivity
import com.ainews.android.R
import com.ainews.android.data.AlertMatch
import com.ainews.android.data.NewsStory

class AlertNotifier(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI News alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Monitor matches from AI News"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyMatches(matches: List<AlertMatch>, stories: List<NewsStory>) {
        if (matches.isEmpty()) return
        if (!canNotify()) return
        ensureChannel()

        val firstMatch = matches.first()
        val firstStory = stories.firstOrNull { it.id == firstMatch.storyId }
        val title = if (matches.size == 1) {
            "AI News alert"
        } else {
            "${matches.size} AI News alerts"
        }
        val body = firstStory?.title ?: firstMatch.explanation
        val intent = Intent(context, MainActivity::class.java).apply {
            firstStory?.let { putExtra(MainActivity.EXTRA_STORY_ID, it.id) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            firstStory?.id?.hashCode() ?: NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "ai-news-alerts"
        private const val NOTIFICATION_ID = 20260918
    }
}
