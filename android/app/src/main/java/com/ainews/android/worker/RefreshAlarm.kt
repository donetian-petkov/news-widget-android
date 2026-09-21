package com.ainews.android.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.NewsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refreshes faster than WorkManager allows. WorkManager will not run a periodic job more
 * often than every fifteen minutes, so anything quicker runs on a repeating alarm instead.
 */
class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = appContext.getSystemService(android.net.ConnectivityManager::class.java)
                val online = manager?.getNetworkCapabilities(manager.activeNetwork)
                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
                if (!online) return@launch
                NewsRepository.initialize(appContext)
                NewsRepository.refreshNow()
                NewsWidget().updateAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 20260920

        fun schedule(context: Context, everyMinutes: Long) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val interval = everyMinutes.coerceAtLeast(1) * 60_000L
            manager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + interval,
                interval,
                pendingIntent(context),
            )
        }

        fun cancel(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            manager.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, RefreshAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
