package com.ainews.android.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ainews.android.MainActivity
import com.ainews.android.data.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = NewsRepository.state.value
            val size = LocalSize.current
            val storyLimit = when {
                size.width < 180.dp || size.height < 130.dp -> 1
                size.height < 220.dp -> 2
                else -> 5
            }
            val showSummary = size.width >= 260.dp && size.height >= 180.dp
            val showActions = size.width >= 220.dp && size.height >= 150.dp
            val showMetrics = size.width >= 220.dp
            val stories = state.prioritizedStories.take(storyLimit)
            LocalContext.current

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFFF8FAFC)))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = state.feedTitle,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF0F172A)),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = state.runtime.statusText,
                    style = TextStyle(color = ColorProvider(Color(0xFF475569))),
                    maxLines = 1,
                )

                if (showMetrics) {
                    Text(
                        text = "${state.visibleStories.size} visible - ${state.monitors.count { it.enabled }} monitors",
                        style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                        maxLines = 1,
                    )
                }

                Spacer(GlanceModifier.height(7.dp))

                Row(horizontalAlignment = Alignment.Start) {
                    WidgetButton(
                        text = if (state.runtime.runtimeEnabled) "Off" else "On",
                        action = actionRunCallback<ToggleRuntimeAction>(),
                    )
                    WidgetButton(
                        text = "Refresh",
                        action = actionRunCallback<RefreshAction>(),
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                stories.forEach { story ->
                    val isAlert = state.alertMatches.any { it.storyId == story.id }
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(
                                ColorProvider(if (isAlert) Color(0xFFFEE2E2) else Color(0xFFFFFFFF)),
                            )
                            .cornerRadius(8.dp)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = story.widgetTitle(isAlert = isAlert),
                            style = TextStyle(
                                color = ColorProvider(if (isAlert) Color(0xFF7F1D1D) else Color(0xFF0F172A)),
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                        )
                        Text(
                            text = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                            style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                            maxLines = 1,
                        )
                        if (showSummary) {
                            Text(
                                text = story.summary,
                                style = TextStyle(color = ColorProvider(Color(0xFF334155))),
                                maxLines = 2,
                            )
                        }
                        if (showActions) {
                            Row(horizontalAlignment = Alignment.Start) {
                                WidgetButton(
                                    text = "Open",
                                    action = actionRunCallback<OpenStoryAction>(
                                        actionParametersOf(storyIdKey to story.id),
                                    ),
                                )
                                WidgetButton(
                                    text = "Hide",
                                    action = actionRunCallback<HideStoryAction>(
                                        actionParametersOf(storyIdKey to story.id),
                                    ),
                                )
                                if (size.width >= 300.dp) {
                                    WidgetButton(
                                        text = "Share",
                                        action = actionRunCallback<ShareStoryAction>(
                                            actionParametersOf(storyIdKey to story.id),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }

                if (stories.isEmpty()) {
                    Text(
                        text = "No visible stories",
                        style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                    )
                }
            }
        }
    }
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewsWidget()
}

class ToggleRuntimeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.toggleRuntime()
        NewsWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.refreshNow()
        NewsWidget().updateAll(context)
    }
}

class HideStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        parameters[storyIdKey]?.let(NewsRepository::hideStory)
        NewsWidget().updateAll(context)
    }
}

class OpenStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        NewsRepository.selectStory(storyId)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_STORY_ID, storyId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }
}

class ShareStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val story = NewsRepository.state.value.stories.firstOrNull { it.id == storyId } ?: return
        withContext(Dispatchers.Main) {
            shareFromWidget(context, story.title, story.sourceUrl)
        }
    }
}

private val storyIdKey = ActionParameters.Key<String>("story-id")

private fun com.ainews.android.data.NewsStory.widgetTitle(isAlert: Boolean): String =
    when {
        isAlert -> "ALERT: $title"
        isNew -> "NEW: $title"
        else -> title
    }
