package com.ainews.android.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Notices the reader arriving at their home screen, as closely as Android allows: the phone
 * being unlocked, or the screen coming on where there is no lock. Registered from the
 * application rather than the manifest, because these two are not delivered to manifest
 * receivers.
 */
class ScreenLookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON ->
                NewStoryExpiryWorker.scheduleAfterLook(context.applicationContext)
        }
    }

    companion object {
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(ScreenLookReceiver(), filter)
        }
    }
}
