package com.ainews.android.ui

import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ainews.android.data.WIDGET_ALL_FEEDS
import com.ainews.android.data.WidgetBackgroundMode
import com.ainews.android.data.WidgetDensityMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.data.WidgetPreset
import com.ainews.android.data.WidgetThemeMode
import com.ainews.android.data.aiBudgetText
import com.ainews.android.data.effectiveFeedSourceIds
import com.ainews.android.data.effectiveWidgetFeedSourceIds
import com.ainews.android.data.setupChecklistItems
import com.ainews.android.network.ImageDiskCache
import com.ainews.android.widget.NewsWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color(0xFFF8FAFC),
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
                    onBack = { NewsRepository.selectStory(null) },
                    onHide = {
                        NewsRepository.hideStory(selectedStory.id)
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
    onShareStory: (NewsStory) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
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

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.prioritizedStories, key = { it.id }) { story ->
                StoryCard(
                    isAlert = state.alertMatches.any { it.storyId == story.id },
                    story = story,
                    onOpen = { onOpenStory(story.id) },
                    onHide = { onHideStory(story.id) },
                    onShare = { onShareStory(story) },
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                )
                Text(
                    text = state.runtime.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569),
                )
            }

            StatusDot(active = state.runtime.runtimeEnabled)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onPower) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.runtime.runtimeEnabled) "Off" else "On")
            }
            HeaderIconButton(
                iconRes = R.drawable.ic_refresh,
                contentDescription = "Refresh",
                enabled = state.runtime.runtimeEnabled,
                onClick = onRefresh,
            )
            OutlinedButton(onClick = onAi) {
                Text(if (state.runtime.aiEnabled) "AI" else "AI off")
            }
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
            OutlinedButton(onClick = onScanMonitors) {
                Text("Scan")
            }
            OutlinedButton(onClick = onEnrich, enabled = state.runtime.runtimeEnabled && state.runtime.aiEnabled) {
                Text("Enrich")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.runtime.autoPowerOffAt == null) {
                    "Auto power-off not set"
                } else {
                    "Auto power-off armed"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF475569),
            )
            OutlinedButton(onClick = { onSetTimeout(1) }, enabled = state.runtime.runtimeEnabled) {
                Text("1h")
            }
            OutlinedButton(onClick = { onSetTimeout(4) }, enabled = state.runtime.runtimeEnabled) {
                Text("4h")
            }
            OutlinedButton(onClick = { onSetTimeout(null) }) {
                Text("Clear")
            }
        }

        Text(
            text = "${state.runtime.aiBudgetText} / ${state.settings.aiDailyBudgetCents}c budget",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B),
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
                        color = Color(0xFF0F172A),
                    )
                    monitor.lastMatchExplanation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
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
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
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
                    color = if (item.complete) Color(0xFF166534) else Color(0xFF334155),
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
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "${feedSources.size} RSS sources",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                TextButton(onClick = onBack) {
                    Text("Done")
                }
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(source.title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(
                        source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
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
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Alert sentences scanned against titles and summaries",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                TextButton(onClick = onBack) {
                    Text("Done")
                }
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                            color = if (monitor.enabled) Color(0xFF166534) else Color(0xFF64748B),
                        )
                        Switch(
                            checked = monitor.enabled,
                            onCheckedChange = { onToggleMonitor(monitor.id) },
                        )
                    }
                    Text(monitor.sentence, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                    monitor.lastMatchExplanation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF334155),
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
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "${stories.size} hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                TextButton(onClick = onBack) {
                    Text("Done")
                }
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(story.source, style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
                    Text(story.title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(
                        story.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334155),
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
                    color = Color(0xFF64748B),
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
    var draft by remember(settings) { mutableStateOf(settings) }
    var keyDraft by remember { mutableStateOf("") }
    var presetNameDraft by remember { mutableStateOf("") }

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
                        color = Color(0xFF0F172A),
                    )
                    Text(
                        text = "Runtime, provider, and monitor controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                    )
                }
                TextButton(onClick = onBack) {
                    Text("Done")
                }
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
                    color = Color(0xFF64748B),
                )
            }
        }

        item {
            SettingsSection("Widget") {
                val selectedWidgetFeedIds = draft.effectiveWidgetFeedSourceIds()
                Text(
                    text = "Feed",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
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
                    color = Color(0xFF64748B),
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
                    color = Color(0xFF64748B),
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
                    color = Color(0xFF64748B),
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
                    color = Color(0xFF64748B),
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
                    text = "Saved views",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
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
                        color = Color(0xFF64748B),
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
                                    widgetStackIndex = 0,
                                )
                            },
                            onDelete = {
                                draft = draft.copy(widgetPresets = draft.widgetPresets.filterNot { it.id == preset.id })
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
            Text(preset.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(
                text = "$feedTitle - ${preset.layoutMode.name} - ${preset.backgroundMode.name} - ${preset.themeMode.name} - ${preset.densityMode.name}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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
            Text(label, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
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
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                    text = story.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64748B),
                )
                if (story.isNew) {
                    Badge("NEW", Color(0xFFDCFCE7), Color(0xFF166534))
                }
                if (isAlert) {
                    Badge("ALERT", Color(0xFFFEE2E2), Color(0xFF991B1B))
                }
            }

            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            )
            Text(
                text = story.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155),
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
                    Badge("AI", Color(0xFFF1F5F9), Color(0xFF475569))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) {
                    Text("Share")
                }
                TextButton(onClick = onHide) {
                    Text("Hide")
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
    onBack: () -> Unit,
    onHide: () -> Unit,
    onShare: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
        item {
            Text(
                text = story.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            )
        }
        item {
            Text(
                text = "${story.source} - ${story.sourceUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
        }
        item { DetailBlock("Summary", story.summary) }
        story.imageUrl?.let { item { StoryImage(imageUrl = it) } }
        story.neutralTitle?.let { item { DetailBlock("Neutral title", it) } }
        story.translation?.let { item { DetailBlock("Translation", it) } }
        story.research?.let { item { DetailBlock("Research", it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare) {
                    Text("Share")
                }
                OutlinedButton(onClick = onHide) {
                    Text("Hide story")
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color(0xFF334155))
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
                .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
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
    val background = if (selected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val foreground = if (selected) Color.White else Color(0xFF334155)
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
