package com.ainews.android.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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
import com.ainews.android.data.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = NewsRepository.state.value
            val stories = state.prioritizedStories.take(3)
            val contextForActions = LocalContext.current

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
                )

                Spacer(GlanceModifier.height(8.dp))

                Row(horizontalAlignment = Alignment.Start) {
                    WidgetButton(
                        text = if (state.runtime.runtimeEnabled) "Power off" else "Power on",
                        action = actionRunCallback<ToggleRuntimeAction>(),
                    )
                    WidgetButton(
                        text = "Refresh",
                        action = actionRunCallback<RefreshAction>(),
                    )
                }

                Spacer(GlanceModifier.height(8.dp))

                stories.forEach { story ->
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    ) {
                        Text(
                            text = story.widgetTitle(
                                isAlert = state.alertMatches.any { it.storyId == story.id },
                            ),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF0F172A)),
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                        )
                        Text(
                            text = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                            style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                            maxLines = 1,
                        )
                        Row(horizontalAlignment = Alignment.Start) {
                            WidgetButton(
                                text = "Hide",
                                action = actionRunCallback<HideStoryAction>(
                                    actionParametersOf(storyIdKey to story.id),
                                ),
                            )
                            WidgetButton(
                                text = "Share",
                                action = actionRunCallback<ShareStoryAction>(
                                    actionParametersOf(storyIdKey to story.id),
                                ),
                            )
                        }
                    }
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
        isAlert -> "ALERT - $title"
        isNew -> "NEW - $title"
        else -> title
    }
