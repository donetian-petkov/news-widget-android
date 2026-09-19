package com.ainews.android.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.ainews.android.data.FeedSource
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.WidgetPreset
import com.ainews.android.data.effectiveFeedSourceIds
import kotlinx.coroutines.launch

class NewsWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val state by NewsRepository.state.collectAsState()
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Surface(color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxSize()) {
                    WidgetConfigureScreen(
                        presets = state.settings.widgetPresets,
                        feedSources = state.feedSources,
                        onCancel = { finish() },
                        onSelect = { presetId ->
                            WidgetInstancePreferences(this).savePresetId(appWidgetId, presetId)
                            scope.launch {
                                val glanceId = GlanceAppWidgetManager(this@NewsWidgetConfigureActivity)
                                    .getGlanceIdBy(appWidgetId)
                                NewsWidget().update(this@NewsWidgetConfigureActivity, glanceId)
                                finishWithResult()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun finishWithResult() {
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

@Composable
private fun WidgetConfigureScreen(
    presets: List<WidgetPreset>,
    feedSources: List<FeedSource>,
    onCancel: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "Widget view",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Choose a saved view for this widget",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }

        item {
            PresetCard(
                title = "Use current settings",
                subtitle = "This widget follows the app-level widget settings",
                onClick = { onSelect(null) },
            )
        }

        if (presets.isEmpty()) {
            item {
                Text(
                    text = "No saved widget views yet. Save views from Settings, then add another widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B),
                )
            }
        }

        items(presets, key = { it.id }) { preset ->
            PresetCard(
                title = preset.name,
                subtitle = presetDescription(preset, feedSources),
                onClick = { onSelect(preset.id) },
            )
        }
    }
}

@Composable
private fun PresetCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
    }
}

private fun presetDescription(preset: WidgetPreset, feedSources: List<FeedSource>): String {
    val feedIds = preset.effectiveFeedSourceIds()
    val feed = when (feedIds.size) {
        0 -> "All feeds"
        1 -> feedSources.firstOrNull { it.id == feedIds.single() }?.title ?: "All feeds"
        else -> "${feedIds.size} feeds"
    }
    return "$feed - ${preset.layoutMode.name} - ${preset.vibe.name} - ${preset.densityMode.name} - ${preset.typographyMode.name}"
}
