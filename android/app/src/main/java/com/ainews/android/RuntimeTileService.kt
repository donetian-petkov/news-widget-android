package com.ainews.android

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import com.ainews.android.data.NewsRepository
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.redrawAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Quick settings tile that turns the fetching runtime on and off without opening the app. */
class RuntimeTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        NewsRepository.initialize(applicationContext)
        render()
    }

    override fun onClick() {
        super.onClick()
        NewsRepository.initialize(applicationContext)
        NewsRepository.toggleRuntime()
        render()
        scope.launch { redrawAllWidgets(applicationContext) }
    }

    private fun render() {
        val tile = qsTile ?: return
        val running = NewsRepository.state.value.runtime.runtimeEnabled
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.contentDescription = if (running) "Runtime on" else "Runtime off"
        tile.updateTile()
    }
}
