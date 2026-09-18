package com.ainews.android.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.NewsStory
import com.ainews.android.data.NewsUiState
import com.ainews.android.widget.NewsWidget
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiNewsApp() {
    val state by NewsRepository.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedStory = state.selectedStory

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC),
        ) {
            if (selectedStory != null) {
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

@Composable
private fun Header(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onPower: () -> Unit,
    onAi: () -> Unit,
    onSetTimeout: (Long?) -> Unit,
    onRestoreHidden: () -> Unit,
    onScanMonitors: () -> Unit,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onPower) {
                Text(if (state.runtime.runtimeEnabled) "Power off" else "Power on")
            }
            OutlinedButton(onClick = onRefresh, enabled = state.runtime.runtimeEnabled) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onAi) {
                Text(if (state.runtime.aiEnabled) "AI on" else "AI off")
            }
        }

        Column(
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }

        OutlinedButton(onClick = onScanMonitors) {
            Text("Scan monitors")
        }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0369A1),
            )
        }

        val hiddenCount = state.stories.count { it.isHidden }
        if (hiddenCount > 0) {
            TextButton(onClick = onRestoreHidden) {
                Text("Restore $hiddenCount hidden")
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
