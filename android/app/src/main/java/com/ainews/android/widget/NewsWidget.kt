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
import com.ainews.android.data.WidgetDensityMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.data.WidgetThemeMode
import com.ainews.android.network.ImageDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.Dp

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
            val metrics = widgetMetrics(state.settings.widgetDensityMode)
            val baseStoryLimit = when {
                size.width < 180.dp || size.height < 130.dp -> 1
                size.height < 220.dp -> 2
                else -> 5
            }
            val storyLimit = (baseStoryLimit + metrics.extraStoryCapacity).coerceAtMost(6)
            val showSummary = size.width >= 260.dp && size.height >= metrics.summaryHeightThreshold
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
            val palette = widgetPalette(
                themeMode = state.settings.widgetThemeMode,
                backgroundMode = state.settings.widgetBackgroundMode,
            )
            LocalContext.current

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(palette.background))
                    .padding(metrics.outerPadding),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = state.widgetFeedTitle,
                    style = TextStyle(
                        color = ColorProvider(palette.header),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.runtime.statusText,
                    style = TextStyle(color = ColorProvider(palette.body)),
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
                        style = TextStyle(color = ColorProvider(palette.muted)),
                        maxLines = 1,
                    )
                }

                Spacer(GlanceModifier.height(metrics.sectionGap))

                stories.forEach { story ->
                    val isAlert = state.alertMatches.any { it.storyId == story.id }
                    val thumbnail = cachedImages[story.id].takeIf { showThumbnails }
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(
                                ColorProvider(if (isAlert) palette.alertCard else palette.card),
                            )
                            .cornerRadius(8.dp)
                            .padding(metrics.cardPadding),
                    ) {
                        if (thumbnail == null) {
                            StoryTextBlock(
                                title = story.widgetTitle(isAlert = isAlert),
                                sourceLine = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                                summary = story.summary,
                                isAlert = isAlert,
                                showSummary = showSummary,
                                palette = palette,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                Image(
                                    provider = ImageProvider(thumbnail),
                                    contentDescription = story.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = GlanceModifier
                                        .size(metrics.thumbnailSize)
                                        .cornerRadius(6.dp),
                                )
                                Spacer(GlanceModifier.width(metrics.thumbnailGap))
                                StoryTextBlock(
                                    title = story.widgetTitle(isAlert = isAlert),
                                    sourceLine = "${story.source} - ${story.topicLabels.joinToString(", ")}",
                                    summary = story.summary,
                                    isAlert = isAlert,
                                    showSummary = showSummary,
                                    palette = palette,
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
                    Spacer(GlanceModifier.height(metrics.cardGap))
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
                            style = TextStyle(color = ColorProvider(palette.muted)),
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
                        style = TextStyle(color = ColorProvider(palette.muted)),
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
    palette: WidgetPalette,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(if (isAlert) palette.alertTitle else palette.storyTitle),
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
        Text(
            text = sourceLine,
            style = TextStyle(color = ColorProvider(palette.muted)),
            maxLines = 1,
        )
        if (showSummary) {
            Text(
                text = summary,
                style = TextStyle(color = ColorProvider(palette.body)),
                maxLines = 2,
            )
        }
    }
}

private data class WidgetPalette(
    val background: Color,
    val card: Color,
    val alertCard: Color,
    val header: Color,
    val storyTitle: Color,
    val alertTitle: Color,
    val body: Color,
    val muted: Color,
)

private data class WidgetMetrics(
    val outerPadding: Dp,
    val cardPadding: Dp,
    val cardGap: Dp,
    val sectionGap: Dp,
    val thumbnailSize: Dp,
    val thumbnailGap: Dp,
    val summaryHeightThreshold: Dp,
    val extraStoryCapacity: Int,
)

private fun widgetMetrics(densityMode: WidgetDensityMode): WidgetMetrics =
    when (densityMode) {
        WidgetDensityMode.Comfortable -> WidgetMetrics(
            outerPadding = 12.dp,
            cardPadding = 8.dp,
            cardGap = 6.dp,
            sectionGap = 7.dp,
            thumbnailSize = 58.dp,
            thumbnailGap = 8.dp,
            summaryHeightThreshold = 180.dp,
            extraStoryCapacity = 0,
        )

        WidgetDensityMode.Compact -> WidgetMetrics(
            outerPadding = 8.dp,
            cardPadding = 6.dp,
            cardGap = 4.dp,
            sectionGap = 5.dp,
            thumbnailSize = 46.dp,
            thumbnailGap = 6.dp,
            summaryHeightThreshold = 260.dp,
            extraStoryCapacity = 1,
        )
    }

private fun widgetPalette(
    themeMode: WidgetThemeMode,
    backgroundMode: WidgetBackgroundMode,
): WidgetPalette =
    when (themeMode) {
        WidgetThemeMode.Light -> WidgetPalette(
            background = when (backgroundMode) {
                WidgetBackgroundMode.Solid -> Color(0xFFF8FAFC)
                WidgetBackgroundMode.Transparent -> Color(0xDDF8FAFC)
            },
            card = Color(0xFFFFFFFF),
            alertCard = Color(0xFFFEE2E2),
            header = Color(0xFF0F172A),
            storyTitle = Color(0xFF0F172A),
            alertTitle = Color(0xFF7F1D1D),
            body = Color(0xFF334155),
            muted = Color(0xFF64748B),
        )

        WidgetThemeMode.Dark -> WidgetPalette(
            background = when (backgroundMode) {
                WidgetBackgroundMode.Solid -> Color(0xFF07111F)
                WidgetBackgroundMode.Transparent -> Color(0xDD07111F)
            },
            card = Color(0xEE0B2035),
            alertCard = Color(0xEE3B1724),
            header = Color(0xFFBDEBFF),
            storyTitle = Color(0xFF22D3EE),
            alertTitle = Color(0xFFFCA5A5),
            body = Color(0xFFE2E8F0),
            muted = Color(0xFF93A4B8),
        )
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
