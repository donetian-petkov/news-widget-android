package com.ainews.android.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.ainews.android.R
import com.ainews.android.data.AiProvider
import com.ainews.android.data.BackendMode
import com.ainews.android.data.FeedSource
import com.ainews.android.data.NewsMonitor
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.NewsStory
import com.ainews.android.data.NewsUiState
import com.ainews.android.data.RuntimeSettings
import com.ainews.android.data.StoryDetailSection
import com.ainews.android.data.WIDGET_ALL_FEEDS
import com.ainews.android.data.WidgetBackgroundMode
import com.ainews.android.data.WidgetDensityMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.data.WidgetPreset
import com.ainews.android.data.WidgetThemeMode
import com.ainews.android.data.WidgetTypographyMode
import com.ainews.android.data.aiBudgetText
import com.ainews.android.data.effectiveFeedSourceIds
import com.ainews.android.data.effectiveWidgetFeedSourceIds
import com.ainews.android.data.setupChecklistItems
import com.ainews.android.network.ImageDiskCache
import com.ainews.android.widget.NewsWidget
import com.ainews.android.widget.NewsWidgetReceiver
import com.ainews.android.widget.WidgetInstancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiNewsApp() {
    val state by NewsRepository.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedStory = state.selectedStory
    var showSettings by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showMonitors by remember { mutableStateOf(false) }
    var showFeeds by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettings || showHidden || showMonitors || showFeeds || selectedStory != null) {
        when {
            selectedStory != null -> NewsRepository.selectStory(null)
            showSettings -> showSettings = false
            showHidden -> showHidden = false
            showMonitors -> showMonitors = false
            showFeeds -> showFeeds = false
        }
    }

    val colorScheme = if (state.settings.widgetThemeMode == WidgetThemeMode.Dark) {
        darkColorScheme(
            background = Color(0xFF07111F),
            surface = Color(0xFF0B2035),
            primary = Color(0xFF8B5CF6),
            onSurface = Color(0xFFEAF6FF),
            onSurfaceVariant = Color(0xFFB7C5D8),
            outlineVariant = Color(0xFF27415B),
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            primary = Color(0xFF6D54B8),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569),
            outlineVariant = Color(0xFFE2E8F0),
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (showSettings) {
                SettingsScreen(
                    settings = state.settings,
                    feedSources = state.feedSources,
                    onBack = { showSettings = false },
                    onSave = {
                        NewsRepository.updateSettings(it)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onSaveKey = NewsRepository::saveProviderKey,
                    onClearKey = NewsRepository::clearProviderKey,
                )
            } else if (showHidden) {
                HiddenStoriesScreen(
                    stories = state.stories.filter { it.isHidden },
                    onBack = { showHidden = false },
                    onRestoreStory = {
                        NewsRepository.restoreStory(it)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onRestoreAll = {
                        NewsRepository.restoreHidden()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                )
            } else if (showMonitors) {
                MonitorScreen(
                    monitors = state.monitors,
                    onBack = { showMonitors = false },
                    onAddMonitor = NewsRepository::addMonitor,
                    onToggleMonitor = NewsRepository::toggleMonitor,
                    onDeleteMonitor = NewsRepository::deleteMonitor,
                    onScanMonitors = {
                        NewsRepository.scanMonitorsNow()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                )
            } else if (showFeeds) {
                FeedSourceScreen(
                    feedSources = state.feedSources,
                    onBack = { showFeeds = false },
                    onAddFeed = NewsRepository::addFeedSource,
                    onDeleteFeed = NewsRepository::deleteFeedSource,
                    onResetFeeds = NewsRepository::resetFeedSources,
                    onRefresh = {
                        scope.launch {
                            NewsRepository.refreshNow()
                            NewsWidget().updateAll(context)
                        }
                    },
                )
            } else if (selectedStory != null) {
                StoryDetail(
                    story = selectedStory,
                    section = state.selectedStorySection,
                    onBack = { NewsRepository.selectStory(null) },
                    onHide = {
                        NewsRepository.hideStory(selectedStory.id)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onTogglePin = {
                        NewsRepository.togglePinned(selectedStory.id)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onShare = { shareStory(context, selectedStory) },
                )
            } else {
                NewsFeed(
                    state = state,
                    onRefresh = {
                        scope.launch {
                            NewsRepository.refreshNow()
                            NewsWidget().updateAll(context)
                        }
                    },
                    onPower = {
                        NewsRepository.toggleRuntime()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onAi = {
                        NewsRepository.toggleAi()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onSetTimeout = {
                        NewsRepository.setAutoPowerOff(it)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onRestoreHidden = {
                        NewsRepository.restoreHidden()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onScanMonitors = {
                        NewsRepository.scanMonitorsNow()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onEnrich = {
                        scope.launch {
                            NewsRepository.enrichVisibleStories()
                            NewsWidget().updateAll(context)
                        }
                    },
                    onSelectTopic = NewsRepository::selectTopic,
                    onOpenSettings = { showSettings = true },
                    onOpenHidden = { showHidden = true },
                    onOpenMonitors = { showMonitors = true },
                    onOpenFeeds = { showFeeds = true },
                    onDismissOnboarding = NewsRepository::dismissOnboarding,
                    onUseLocalAi = {
                        NewsRepository.useLocalAiProvider()
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onOpenStory = NewsRepository::selectStory,
                    onHideStory = {
                        NewsRepository.hideStory(it)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onTogglePinStory = {
                        NewsRepository.togglePinned(it)
                        scope.launch { NewsWidget().updateAll(context) }
                    },
                    onShareStory = { shareStory(context, it) },
                )
            }
        }
    }
}

@Composable
private fun NewsFeed(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onPower: () -> Unit,
    onAi: () -> Unit,
    onSetTimeout: (Long?) -> Unit,
    onRestoreHidden: () -> Unit,
    onScanMonitors: () -> Unit,
    onEnrich: () -> Unit,
    onSelectTopic: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenMonitors: () -> Unit,
    onOpenFeeds: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onUseLocalAi: () -> Unit,
    onOpenStory: (String) -> Unit,
    onHideStory: (String) -> Unit,
    onTogglePinStory: (String) -> Unit,
    onShareStory: (NewsStory) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        item {
            Header(
                state = state,
                onRefresh = onRefresh,
                onPower = onPower,
                onAi = onAi,
                onSetTimeout = onSetTimeout,
                onRestoreHidden = onRestoreHidden,
                onScanMonitors = onScanMonitors,
                onEnrich = onEnrich,
                onSelectTopic = onSelectTopic,
                onOpenSettings = onOpenSettings,
                onOpenHidden = onOpenHidden,
                onOpenMonitors = onOpenMonitors,
                onOpenFeeds = onOpenFeeds,
                onDismissOnboarding = onDismissOnboarding,
                onUseLocalAi = onUseLocalAi,
            )
        }

        items(state.prioritizedStories, key = { it.id }) { story ->
            StoryCard(
                isAlert = state.alertMatches.any { it.storyId == story.id },
                story = story,
                onOpen = { onOpenStory(story.id) },
                onHide = { onHideStory(story.id) },
                onTogglePin = { onTogglePinStory(story.id) },
                onShare = { onShareStory(story) },
            )
        }

        if (state.prioritizedStories.isEmpty()) {
            item {
                Text(
                    text = "No visible stories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onPower: () -> Unit,
    onAi: () -> Unit,
    onSetTimeout: (Long?) -> Unit,
    onRestoreHidden: () -> Unit,
    onScanMonitors: () -> Unit,
    onEnrich: () -> Unit,
    onSelectTopic: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenMonitors: () -> Unit,
    onOpenFeeds: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onUseLocalAi: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = state.feedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.runtime.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusDot(active = state.runtime.runtimeEnabled)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeaderActionButton(
                text = if (state.runtime.runtimeEnabled) "Off" else "On",
                iconRes = R.drawable.ic_power,
                selected = state.runtime.runtimeEnabled,
                onClick = onPower,
            )
            HeaderIconButton(
                iconRes = R.drawable.ic_refresh,
                contentDescription = "Refresh",
                enabled = state.runtime.runtimeEnabled,
                onClick = onRefresh,
            )
            HeaderActionButton(
                text = if (state.runtime.aiEnabled) "AI" else "AI off",
                selected = state.runtime.aiEnabled,
                onClick = onAi,
            )
            HeaderIconButton(
                iconRes = R.drawable.ic_settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
            )
            HeaderIconButton(
                iconRes = R.drawable.ic_monitor,
                contentDescription = "Monitors",
                onClick = onOpenMonitors,
            )
            HeaderIconButton(
                iconRes = R.drawable.ic_feeds,
                contentDescription = "Feeds",
                onClick = onOpenFeeds,
            )
            HeaderActionButton(text = "Scan", onClick = onScanMonitors)
            HeaderActionButton(
                text = "Enrich",
                enabled = state.runtime.runtimeEnabled && state.runtime.aiEnabled,
                onClick = onEnrich,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.runtime.autoPowerOffAt == null) {
                    "Auto power-off not set"
                } else {
                    "Auto power-off armed"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HeaderActionButton(text = "1h", enabled = state.runtime.runtimeEnabled, onClick = { onSetTimeout(1) })
            HeaderActionButton(text = "4h", enabled = state.runtime.runtimeEnabled, onClick = { onSetTimeout(4) })
            HeaderActionButton(text = "Clear", onClick = { onSetTimeout(null) })
        }

        Text(
            text = "${state.runtime.aiBudgetText} / ${state.settings.aiDailyBudgetCents}c budget",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TopicChip(
                text = "All",
                selected = state.selectedTopic == null,
                onClick = { onSelectTopic(null) },
            )
            state.availableTopics.forEach { topic ->
                TopicChip(
                    text = topic,
                    selected = state.selectedTopic == topic,
                    onClick = { onSelectTopic(topic) },
                )
            }
        }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0369A1),
            )
        }

        if (!state.settings.onboardingDismissed) {
            SetupChecklistCard(
                state = state,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings,
                onOpenFeeds = onOpenFeeds,
                onUseLocalAi = onUseLocalAi,
                onDismiss = onDismissOnboarding,
            )
        }

        val hiddenCount = state.stories.count { it.isHidden }
        if (hiddenCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenHidden) {
                    Text("Hidden $hiddenCount")
                }
                TextButton(onClick = onRestoreHidden) {
                    Text("Restore all")
                }
            }
        }

        state.monitors.filter { it.lastMatchStoryId != null }.forEach { monitor ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Alert match ${monitor.lastMatchConfidence?.let { "${(it * 100).toInt()}%" } ?: ""}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075985),
                    )
                    Text(
                        text = monitor.sentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    monitor.lastMatchExplanation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderActionButton(
    text: String,
    iconRes: Int? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.surface
        enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = foreground,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BackIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(22.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupChecklistCard(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeeds: () -> Unit,
    onUseLocalAi: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = setupChecklistItems(state)
    val completedCount = items.count { it.complete }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Setup checklist",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075985),
                    )
                    Text(
                        text = "$completedCount/${items.size} ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0369A1),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Hide")
                }
            }
            items.forEach { item ->
                Text(
                    text = "${if (item.complete) "Done" else "Todo"} - ${item.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.complete) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (items.any { it.id == "stories" && !it.complete }) {
                    Button(onClick = onRefresh, enabled = state.runtime.runtimeEnabled) {
                        Text("Refresh now")
                    }
                }
                if (items.any { it.id == "ai" && !it.complete }) {
                    OutlinedButton(onClick = onUseLocalAi) {
                        Text("Use local AI")
                    }
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
                OutlinedButton(onClick = onOpenFeeds) {
                    Text("Feeds")
                }
            }
        }
    }
}

@Composable
private fun FeedSourceScreen(
    feedSources: List<FeedSource>,
    onBack: () -> Unit,
    onAddFeed: (String, String) -> Unit,
    onDeleteFeed: (String) -> Unit,
    onResetFeeds: () -> Unit,
    onRefresh: () -> Unit,
) {
    var titleDraft by remember { mutableStateOf("") }
    var urlDraft by remember { mutableStateOf("") }

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
                        text = "Feeds",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${feedSources.size} RSS sources",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BackIconButton(onClick = onBack)
            }
        }

        item {
            SettingsSection("New RSS feed") {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = { Text("Feed title") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("RSS URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onAddFeed(titleDraft, urlDraft)
                            titleDraft = ""
                            urlDraft = ""
                        },
                        enabled = titleDraft.isNotBlank() && urlDraft.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                    OutlinedButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onResetFeeds) {
                        Text("Defaults")
                    }
                }
            }
        }

        items(feedSources, key = { it.id }) { source ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(source.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onDeleteFeed(source.id) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorScreen(
    monitors: List<NewsMonitor>,
    onBack: () -> Unit,
    onAddMonitor: (String) -> Unit,
    onToggleMonitor: (String) -> Unit,
    onDeleteMonitor: (String) -> Unit,
    onScanMonitors: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

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
                        text = "Monitors",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Alert sentences scanned against titles and summaries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BackIconButton(onClick = onBack)
            }
        }

        item {
            SettingsSection("New monitor") {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Alert sentence") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onAddMonitor(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                    OutlinedButton(onClick = onScanMonitors) {
                        Text("Scan now")
                    }
                }
            }
        }

        items(monitors, key = { it.id }) { monitor ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (monitor.enabled) "Enabled" else "Paused",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (monitor.enabled) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = monitor.enabled,
                            onCheckedChange = { onToggleMonitor(monitor.id) },
                        )
                    }
                    Text(monitor.sentence, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    monitor.lastMatchExplanation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onDeleteMonitor(monitor.id) }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenStoriesScreen(
    stories: List<NewsStory>,
    onBack: () -> Unit,
    onRestoreStory: (String) -> Unit,
    onRestoreAll: () -> Unit,
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
                        text = "Hidden Stories",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${stories.size} hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BackIconButton(onClick = onBack)
            }
        }

        if (stories.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onRestoreAll, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore all hidden stories")
                }
            }
        }

        items(stories, key = { it.id }) { story ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(story.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(story.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        story.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(onClick = { onRestoreStory(story.id) }) {
                        Text("Restore")
                    }
                }
            }
        }

        if (stories.isEmpty()) {
            item {
                Text(
                    text = "No hidden stories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    settings: RuntimeSettings,
    feedSources: List<FeedSource>,
    onBack: () -> Unit,
    onSave: (RuntimeSettings) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(settings) { mutableStateOf(settings) }
    var keyDraft by remember { mutableStateOf("") }
    var presetNameDraft by remember { mutableStateOf("") }
    var widgetRefreshToken by remember { mutableStateOf(0) }
    val widgetInstances by produceState<List<WidgetInstanceInfo>>(initialValue = emptyList(), widgetRefreshToken) {
        value = loadWidgetInstances(context)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Runtime, provider, and monitor controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BackIconButton(onClick = onBack)
            }
        }

        item {
            SettingsSection("Runtime mode") {
                ToggleRow(
                    label = "Native runtime",
                    description = "Phone fetches RSS and runs local state",
                    checked = draft.backendMode == BackendMode.NativeRuntime,
                    onCheckedChange = {
                        draft = draft.copy(
                            backendMode = if (it) BackendMode.NativeRuntime else BackendMode.RemoteBackend,
                        )
                    },
                )
                OutlinedTextField(
                    value = draft.remoteBackendUrl,
                    onValueChange = { draft = draft.copy(remoteBackendUrl = it) },
                    label = { Text("Remote backend URL") },
                    enabled = draft.backendMode == BackendMode.RemoteBackend,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            SettingsSection("AI provider") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.values().forEach { provider ->
                        TopicChip(
                            text = provider.name,
                            selected = draft.aiProvider == provider,
                            onClick = { draft = draft.copy(aiProvider = provider) },
                        )
                    }
                }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text(if (settings.providerKeySaved) "Provider key saved" else "Provider key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        onSaveKey(keyDraft)
                        keyDraft = ""
                    }) {
                        Text("Save key")
                    }
                    OutlinedButton(onClick = onClearKey, enabled = settings.providerKeySaved) {
                        Text("Clear key")
                    }
                }
            }
        }

        item {
            SettingsSection("Cadence and budget") {
                NumberField(
                    label = "Fetch cadence minutes",
                    value = draft.fetchCadenceMinutes.toString(),
                    onValueChange = {
                        draft = draft.copy(fetchCadenceMinutes = it.toLongOrNull() ?: draft.fetchCadenceMinutes)
                    },
                )
                NumberField(
                    label = "Monitor scan hour",
                    value = draft.monitorScanHour.toString(),
                    onValueChange = {
                        draft = draft.copy(monitorScanHour = it.toIntOrNull() ?: draft.monitorScanHour)
                    },
                )
                NumberField(
                    label = "AI daily budget cents",
                    value = draft.aiDailyBudgetCents.toString(),
                    onValueChange = {
                        draft = draft.copy(aiDailyBudgetCents = it.toIntOrNull() ?: draft.aiDailyBudgetCents)
                    },
                )
                Text(
                    text = "Current usage: ${settings.providerKeySaved.let { if (it) "provider key saved" else "local fallback" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection("Widget") {
                val selectedWidgetFeedIds = draft.effectiveWidgetFeedSourceIds()
                Text(
                    text = "Feed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TopicChip(
                        text = "All feeds",
                        selected = selectedWidgetFeedIds.isEmpty(),
                        onClick = {
                            draft = draft.copy(
                                widgetFeedSourceId = WIDGET_ALL_FEEDS,
                                widgetFeedSourceIds = emptyList(),
                                widgetStackIndex = 0,
                            )
                        },
                    )
                    feedSources.forEach { source ->
                        TopicChip(
                            text = source.title,
                            selected = source.id in selectedWidgetFeedIds,
                            onClick = {
                                val nextIds = if (source.id in selectedWidgetFeedIds) {
                                    selectedWidgetFeedIds - source.id
                                } else {
                                    selectedWidgetFeedIds + source.id
                                }
                                draft = draft.copy(
                                    widgetFeedSourceId = nextIds.singleOrNull() ?: WIDGET_ALL_FEEDS,
                                    widgetFeedSourceIds = nextIds,
                                    widgetStackIndex = 0,
                                )
                            },
                        )
                    }
                }

                Text(
                    text = "Layout",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WidgetLayoutMode.values().forEach { mode ->
                        TopicChip(
                            text = mode.name,
                            selected = draft.widgetLayoutMode == mode,
                            onClick = { draft = draft.copy(widgetLayoutMode = mode) },
                        )
                    }
                }

                Text(
                    text = "Background",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WidgetBackgroundMode.values().forEach { mode ->
                        TopicChip(
                            text = mode.name,
                            selected = draft.widgetBackgroundMode == mode,
                            onClick = { draft = draft.copy(widgetBackgroundMode = mode) },
                        )
                    }
                }

                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WidgetThemeMode.values().forEach { mode ->
                        TopicChip(
                            text = mode.name,
                            selected = draft.widgetThemeMode == mode,
                            onClick = { draft = draft.copy(widgetThemeMode = mode) },
                        )
                    }
                }

                Text(
                    text = "Density",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WidgetDensityMode.values().forEach { mode ->
                        TopicChip(
                            text = mode.name,
                            selected = draft.widgetDensityMode == mode,
                            onClick = { draft = draft.copy(widgetDensityMode = mode) },
                        )
                    }
                }

                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WidgetTypographyMode.values().forEach { mode ->
                        TopicChip(
                            text = mode.name,
                            selected = draft.widgetTypographyMode == mode,
                            onClick = { draft = draft.copy(widgetTypographyMode = mode) },
                        )
                    }
                }

                Text(
                    text = "Saved views",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = presetNameDraft,
                    onValueChange = { presetNameDraft = it },
                    label = { Text("View name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val name = presetNameDraft.trim()
                        if (name.isNotBlank()) {
                            val preset = WidgetPreset(
                                id = "widget-${System.currentTimeMillis()}",
                                name = name,
                                feedSourceId = selectedWidgetFeedIds.singleOrNull() ?: WIDGET_ALL_FEEDS,
                                feedSourceIds = selectedWidgetFeedIds,
                                layoutMode = draft.widgetLayoutMode,
                                backgroundMode = draft.widgetBackgroundMode,
                                themeMode = draft.widgetThemeMode,
                                densityMode = draft.widgetDensityMode,
                                typographyMode = draft.widgetTypographyMode,
                            )
                            draft = draft.copy(widgetPresets = (draft.widgetPresets + preset).takeLast(12))
                            presetNameDraft = ""
                        }
                    },
                    enabled = presetNameDraft.isNotBlank(),
                ) {
                    Text("Save current view")
                }
                if (draft.widgetPresets.isEmpty()) {
                    Text(
                        text = "No saved widget views yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    draft.widgetPresets.forEach { preset ->
                        SavedWidgetPresetRow(
                            preset = preset,
                            feedTitle = widgetFeedTitle(preset.effectiveFeedSourceIds(), feedSources),
                            onApply = {
                                val presetFeedIds = preset.effectiveFeedSourceIds()
                                draft = draft.copy(
                                    widgetFeedSourceId = presetFeedIds.singleOrNull() ?: WIDGET_ALL_FEEDS,
                                    widgetFeedSourceIds = presetFeedIds,
                                    widgetLayoutMode = preset.layoutMode,
                                    widgetBackgroundMode = preset.backgroundMode,
                                    widgetThemeMode = preset.themeMode,
                                    widgetDensityMode = preset.densityMode,
                                    widgetTypographyMode = preset.typographyMode,
                                    widgetStackIndex = 0,
                                )
                            },
                            onDelete = {
                                draft = draft.copy(widgetPresets = draft.widgetPresets.filterNot { it.id == preset.id })
                            },
                        )
                    }
                }

                Text(
                    text = "Active widgets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (widgetInstances.isEmpty()) {
                    Text(
                        text = "No active widgets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    widgetInstances.forEachIndexed { index, widget ->
                        WidgetInstanceRow(
                            title = "Widget ${index + 1}",
                            selectedPresetId = widget.presetId,
                            presets = draft.widgetPresets,
                            feedSources = feedSources,
                            onSelect = { presetId ->
                                WidgetInstancePreferences(context).savePresetId(widget.appWidgetId, presetId)
                                widgetRefreshToken += 1
                                scope.launch {
                                    runCatching {
                                        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widget.appWidgetId)
                                        NewsWidget().update(context, glanceId)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save settings")
            }
        }
    }
}

private fun widgetFeedTitle(feedIds: List<String>, feedSources: List<FeedSource>): String =
    when (feedIds.size) {
        0 -> "All feeds"
        1 -> feedSources.firstOrNull { it.id == feedIds.single() }?.title ?: "All feeds"
        else -> "${feedIds.size} feeds"
    }

@Composable
private fun SavedWidgetPresetRow(
    preset: WidgetPreset,
    feedTitle: String,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(preset.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "$feedTitle - ${preset.layoutMode.name} - ${preset.backgroundMode.name} - ${preset.themeMode.name} - ${preset.densityMode.name} - ${preset.typographyMode.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onApply) {
            Text("Apply")
        }
        TextButton(onClick = onDelete) {
            Text("Delete")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetInstanceRow(
    title: String,
    selectedPresetId: String?,
    presets: List<WidgetPreset>,
    feedSources: List<FeedSource>,
    onSelect: (String?) -> Unit,
) {
    val selectedPreset = presets.firstOrNull { it.id == selectedPresetId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = selectedPreset?.let { "Using ${it.name}" } ?: "Using current settings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TopicChip(
                text = "Current",
                selected = selectedPresetId == null || selectedPreset == null,
                onClick = { onSelect(null) },
            )
            presets.forEach { preset ->
                TopicChip(
                    text = preset.name,
                    selected = selectedPresetId == preset.id,
                    onClick = { onSelect(preset.id) },
                )
            }
        }
        selectedPreset?.let { preset ->
            Text(
                text = "${widgetFeedTitle(preset.effectiveFeedSourceIds(), feedSources)} - ${preset.layoutMode.name} - ${preset.themeMode.name} - ${preset.densityMode.name} - ${preset.typographyMode.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class WidgetInstanceInfo(
    val appWidgetId: Int,
    val presetId: String?,
)

private fun loadWidgetInstances(context: android.content.Context): List<WidgetInstanceInfo> {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, NewsWidgetReceiver::class.java)
    val preferences = WidgetInstancePreferences(context)
    return manager.getAppWidgetIds(component)
        .sorted()
        .map { appWidgetId ->
            WidgetInstanceInfo(
                appWidgetId = appWidgetId,
                presetId = preferences.presetId(appWidgetId),
            )
        }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryCard(
    isAlert: Boolean,
    story: NewsStory,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = story.storyMetaLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (story.isNew) {
                    Badge("NEW", Color(0xFFDCFCE7), Color(0xFF166534))
                }
                if (isAlert) {
                    Badge("ALERT", Color(0xFFFEE2E2), Color(0xFF991B1B))
                }
                if (story.isPinned) {
                    Badge("PIN", Color(0xFFFDE68A), Color(0xFF92400E))
                }
            }

            Text(
                text = story.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = story.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            story.imageUrl?.let {
                StoryImage(imageUrl = it)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                story.topicLabels.forEach {
                    Badge(it, Color(0xFFE0F2FE), Color(0xFF075985))
                }
                if (story.aiFieldsAvailable) {
                    Badge("AI", Color(0xFFF1F5F9), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) {
                    Text("Share")
                }
                TextButton(onClick = onHide) {
                    Text("Hide")
                }
                TextButton(onClick = onTogglePin) {
                    Text(if (story.isPinned) "Unpin" else "Pin")
                }
                TextButton(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun StoryDetail(
    story: NewsStory,
    section: StoryDetailSection,
    onBack: () -> Unit,
    onHide: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
) {
    val detailBlocks = story.detailBlocks(section)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            BackIconButton(onClick = onBack)
        }
        item {
            Text(
                text = story.displayTitle(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Text(
                text = "${story.storyMetaLine()} - ${story.sourceUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detailBlocks.forEach { block ->
            item { DetailBlock(block.title, block.body, highlighted = block.section == section && section != StoryDetailSection.Story) }
        }
        story.imageUrl?.let { item { StoryImage(imageUrl = it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare) {
                    Text("Share")
                }
                OutlinedButton(onClick = onTogglePin) {
                    Text(if (story.isPinned) "Unpin story" else "Pin story")
                }
                OutlinedButton(onClick = onHide) {
                    Text("Hide story")
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(title: String, body: String, highlighted: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFFE0F2FE) else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, if (highlighted) Color(0xFF38BDF8) else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StoryImage(imageUrl: String) {
    val context = LocalContext.current
    val imageDiskCache = remember { ImageDiskCache() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageUrl) {
        value = withContext(Dispatchers.IO) {
            imageDiskCache.loadBitmap(context, imageUrl)?.asImageBitmap()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    val foreground = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(
                color = if (active) Color(0xFF16A34A) else Color(0xFF94A3B8),
                shape = RoundedCornerShape(7.dp),
            ),
    )
}

private fun shareStory(context: android.content.Context, story: NewsStory) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${story.title}\n${story.sourceUrl}")
    }
    context.startActivity(Intent.createChooser(intent, "Share story"))
}

private fun NewsStory.displayTitle(): String =
    neutralTitle?.takeIf { it.isNotBlank() } ?: title

private fun NewsStory.storyMetaLine(): String =
    listOfNotNull(
        source,
        formatStoryTime(publishedAt),
        topicLabels.take(2).joinToString(", ").takeIf { it.isNotBlank() },
        "AI".takeIf { aiFieldsAvailable },
    ).joinToString(" - ")

private data class StoryDetailBlock(
    val section: StoryDetailSection,
    val title: String,
    val body: String,
)

private fun NewsStory.detailBlocks(selectedSection: StoryDetailSection): List<StoryDetailBlock> {
    val blocks = buildList {
        add(StoryDetailBlock(StoryDetailSection.Summary, "Summary", summary))
        neutralTitle?.takeIf { it.isNotBlank() }?.let {
            add(StoryDetailBlock(StoryDetailSection.Story, "Neutral title", it))
        }
        research?.takeIf { it.isNotBlank() }?.let {
            add(StoryDetailBlock(StoryDetailSection.Research, "Research", it))
        }
        translation?.takeIf { it.isNotBlank() }?.let {
            add(StoryDetailBlock(StoryDetailSection.Translation, "Translation", it))
        }
    }
    if (selectedSection == StoryDetailSection.Story) return blocks
    return blocks.sortedBy { if (it.section == selectedSection) 0 else 1 }
}

private fun formatStoryTime(epochMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()))
    }.getOrDefault("")
