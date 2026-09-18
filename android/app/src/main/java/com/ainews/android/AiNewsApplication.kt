package com.ainews.android

import android.app.Application
import com.ainews.android.data.NewsRepository
import com.ainews.android.notifications.AlertNotifier
import com.ainews.android.worker.MonitorScanWorker
import com.ainews.android.worker.RefreshNewsWorker

class AiNewsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlertNotifier(this).ensureChannel()
        NewsRepository.initialize(this)
        RefreshNewsWorker.schedule(this, NewsRepository.state.value.settings.fetchCadenceMinutes)
        MonitorScanWorker.schedule(this, NewsRepository.state.value.settings.monitorScanHour)
    }
}
