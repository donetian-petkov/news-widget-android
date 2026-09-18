package com.ainews.android.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ainews.android.MainActivity
import com.ainews.android.R
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.WidgetBackgroundMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.network.ImageDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val imageDiskCache = ImageDiskCache()
        val cachedImages = NewsRepository.state.value.widgetStories
            .take(5)
            .mapNotNull { story ->
                val imageUrl = story.imageUrl ?: return@mapNotNull null
                val bitmap = imageDiskCache.loadCachedBitmap(context, imageUrl) ?: return@mapNotNull null
                story.id to bitmap
            }
            .toMap()

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
            val showThumbnails = size.width >= 260.dp && size.height >= 170.dp
            val stackMode = state.settings.widgetLayoutMode == WidgetLayoutMode.Stack
            val allWidgetStories = state.widgetStories
            val stackIndex = state.widgetStackIndex
            val stories = if (stackMode) {
                allWidgetStories.drop(stackIndex).take(1)
            } else {
                allWidgetStories.take(storyLimit)
            }
            val widgetBackground = when (state.settings.widgetBackgroundMode) {
                WidgetBackgroundMode.Solid -> Color(0xFFF8FAFC)
                WidgetBackgroundMode.Transparent -> Color(0xDDF8FAFC)
            }
            LocalContext.current

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(widgetBackground))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = state.widgetFeedTitle,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF0F172A)),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.runtime.statusText,
                    style = TextStyle(color = ColorProvider(Color(0xFF475569))),
                    maxLines = 1,
                )

                Row(
                    horizontalAlignment = Alignment.Start,
                ) {
                    WidgetIconButton(
                        iconRes = R.drawable.ic_power,
                        contentDescription = if (state.runtime.runtimeEnabled) "Power off" else "Power on",
                        action = actionRunCallback<ToggleRuntimeAction>(),
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_refresh,
                        contentDescription = "Refresh",
                        action = actionRunCallback<RefreshAction>(),
                    )
                }

                if (showMetrics) {
                    Text(
                        text = "${allWidgetStories.size} stories - ${state.settings.widgetLayoutMode.name}",
                        style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                        maxLines = 1,
                    )
                }

                Spacer(GlanceModifier.height(7.dp))

                stories.forEach { story ->
                    val isAlert = state.alertMatches.any { it.storyId == story.id }
                    val thumbnail = cachedImages[story.id].takeIf { showThumbnails }
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(
                                ColorProvider(if (isAlert) Color(0xFFFEE2E2) else Color(0xFFFFFFFF)),
                            )
                            .cornerRadius(8.dp)
                            .padding(8.dp),
                    ) {
                        if (thumbnail == null) {
                            StoryTextBlock(
                                title = story.widgetTitle(isAlert = isAlert),
                                sourceLine = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                                summary = story.summary,
                                isAlert = isAlert,
                                showSummary = showSummary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                Image(
                                    provider = ImageProvider(thumbnail),
                                    contentDescription = story.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier
                                        .size(58.dp)
                                        .cornerRadius(6.dp),
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                StoryTextBlock(
                                    title = story.widgetTitle(isAlert = isAlert),
                                    sourceLine = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                                    summary = story.summary,
                                    isAlert = isAlert,
                                    showSummary = showSummary,
                                )
                            }
                        }
                        if (showActions) {
                            Row(horizontalAlignment = Alignment.Start) {
                                WidgetIconButton(
                                    iconRes = R.drawable.ic_open,
                                    contentDescription = "Open story",
                                    action = actionRunCallback<OpenStoryAction>(
                                        actionParametersOf(storyIdKey to story.id),
                                    ),
                                )
                                WidgetIconButton(
                                    iconRes = R.drawable.ic_hide,
                                    contentDescription = "Hide story",
                                    action = actionRunCallback<HideStoryAction>(
                                        actionParametersOf(storyIdKey to story.id),
                                    ),
                                )
                                if (size.width >= 300.dp) {
                                    WidgetIconButton(
                                        iconRes = R.drawable.ic_share,
                                        contentDescription = "Share story",
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

                if (stackMode && allWidgetStories.size > 1 && showMetrics) {
                    Row(horizontalAlignment = Alignment.CenterHorizontally) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_arrow_up,
                            contentDescription = "Previous story",
                            action = actionRunCallback<PreviousStackStoryAction>(),
                        )
                        Text(
                            text = "${stackIndex + 1}/${allWidgetStories.size}",
                            style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                            maxLines = 1,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_arrow_down,
                            contentDescription = "Next story",
                            action = actionRunCallback<NextStackStoryAction>(),
                        )
                    }
                }

                if (allWidgetStories.isEmpty()) {
                    Text(
                        text = "No visible stories",
                        style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StoryTextBlock(
    title: String,
    sourceLine: String,
    summary: String,
    isAlert: Boolean,
    showSummary: Boolean,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(if (isAlert) Color(0xFF7F1D1D) else Color(0xFF0F172A)),
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Text(
            text = sourceLine,
            style = TextStyle(color = ColorProvider(Color(0xFF64748B))),
            maxLines = 1,
        )
        if (showSummary) {
            Text(
                text = summary,
                style = TextStyle(color = ColorProvider(Color(0xFF334155))),
                maxLines = 2,
            )
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

class PreviousStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.moveWidgetStack(-1)
        NewsWidget().updateAll(context)
    }
}

class NextStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.moveWidgetStack(1)
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
