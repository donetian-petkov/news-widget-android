package com.ainews.android.widget

import android.content.Context
import android.content.res.Configuration
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainews.android.MainActivity
import com.ainews.android.R
import com.ainews.android.data.FetchStatus
import com.ainews.android.data.FontScale
import com.ainews.android.data.ThemePalette
import com.ainews.android.data.dynamicThemePalette
import com.ainews.android.data.KeywordMatcher
import com.ainews.android.data.WIDGET_FILTERED_FEED
import com.ainews.android.data.NewsStory
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.StoryDetailSection
import com.ainews.android.data.WidgetBackgroundMode
import com.ainews.android.data.WidgetDensityMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.data.AppVibe
import com.ainews.android.data.WidgetTypographyMode
import com.ainews.android.data.effectiveFeedSourceIds
import com.ainews.android.data.effectiveWidgetFeedSourceIds
import com.ainews.android.network.ImageDiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val WIDGET_IMAGE_LIMIT = 6
private const val STALE_FETCH_MILLIS = 3L * 60L * 1000L

class NewsWidget : GlanceAppWidget() {
    /**
     * Without this, Glance reports the widget's declared minimum size instead of the size it
     * actually has on the home screen, so a large widget would still draw a single story.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val imageDiskCache = ImageDiskCache()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val instancePreferences = WidgetInstancePreferences(context)
        val cachedImages = NewsRepository.state.value.prioritizedStories
            .take(WIDGET_IMAGE_LIMIT)
            .mapNotNull { story ->
                val imageUrl = story.imageUrl ?: return@mapNotNull null
                val bitmap = imageDiskCache.loadCachedThumbnail(context, imageUrl) ?: return@mapNotNull null
                story.id to bitmap
            }
            .toMap()

        provideContent {
            val state = NewsRepository.state.value
            val preset = instancePreferences.presetId(appWidgetId)
                ?.let { presetId -> state.settings.widgetPresets.firstOrNull { it.id == presetId } }
            val instanceLayoutMode = instancePreferences.layoutMode(appWidgetId)
                ?.let { modeName -> runCatching { WidgetLayoutMode.valueOf(modeName) }.getOrNull() }
            val settings = state.settings.copy(
                widgetFeedSourceId = preset?.feedSourceId ?: state.settings.widgetFeedSourceId,
                widgetFeedSourceIds = preset?.effectiveFeedSourceIds() ?: state.settings.effectiveWidgetFeedSourceIds(),
                widgetLayoutMode = instanceLayoutMode ?: preset?.layoutMode ?: state.settings.widgetLayoutMode,
                widgetBackgroundMode = preset?.backgroundMode ?: state.settings.widgetBackgroundMode,
                appVibe = preset?.vibe ?: state.settings.appVibe,
                widgetDensityMode = preset?.densityMode ?: state.settings.widgetDensityMode,
                widgetTypographyMode = preset?.typographyMode ?: state.settings.widgetTypographyMode,
                widgetStackIndex = instancePreferences.stackIndex(appWidgetId),
            )
            val selectedFeedIds = settings.effectiveWidgetFeedSourceIds().toSet()
            val showFilteredFeed = WIDGET_FILTERED_FEED in selectedFeedIds
            val selectedFeeds = state.feedSources.filter { it.id in selectedFeedIds }
            val widgetFeedTitle = when {
                showFilteredFeed -> "Filtered Feed"
                selectedFeeds.isEmpty() -> "All Feeds"
                selectedFeeds.size == 1 -> selectedFeeds.single().title
                else -> "${selectedFeeds.size} Feeds"
            }
            val selectedFeedTitles = selectedFeeds.map { it.title }.toSet()
            val allWidgetStories = if (showFilteredFeed) {
                state.prioritizedStories.filter { KeywordMatcher.matches(settings.keywords, it) }
            } else {
                state.prioritizedStories.filter { story ->
                    selectedFeedTitles.isEmpty() || story.source in selectedFeedTitles
                }
            }.filter { story -> !instancePreferences.unreadOnly(appWidgetId) || !story.isRead }
            val size = LocalSize.current
            val metrics = widgetMetrics(settings.widgetDensityMode)
            val type = widgetTypography(settings.widgetTypographyMode, settings.widgetFontScale)
            val preferredStoryCount = instancePreferences.storyCount(appWidgetId)
            val unreadOnly = instancePreferences.unreadOnly(appWidgetId)
            val expandedStoryId = instancePreferences.expandedStoryId(appWidgetId)
            val tokensToday = state.usageRecords
                .filter { it.createdAt > System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
                .sumOf { it.tokens }
            val baseStoryLimit = when {
                size.width < 180.dp || size.height < 130.dp -> 1
                size.height < 220.dp -> 2
                else -> preferredStoryCount
            }
            val storyLimit = (baseStoryLimit + metrics.extraStoryCapacity).coerceAtMost(100)
            // A fetch that died with the process would otherwise leave the spinner up forever.
            val fetching = state.runtime.lastFetchStatus == FetchStatus.Fetching &&
                (System.currentTimeMillis() - (state.runtime.lastFetchStartedAt ?: 0L)) < STALE_FETCH_MILLIS
            val showActions = size.width >= 220.dp && size.height >= 150.dp
            val showStatusText = size.width >= 260.dp
            val stackMode = settings.widgetLayoutMode == WidgetLayoutMode.Stack
            val stackIndex = normalizedStackIndex(settings.widgetStackIndex, allWidgetStories.size)
            val stories = if (stackMode) {
                allWidgetStories.drop(stackIndex).take(1)
            } else {
                allWidgetStories.take(storyLimit)
            }
            val systemInDarkMode = (
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES
            val themePalette = settings.takeIf { it.useMaterialYou }
                ?.let { dynamicThemePalette(context, settings.appVibe.isDark(systemInDarkMode)) }
                ?: settings.appVibe.palette(systemInDarkMode)
            val palette = widgetPalette(
                palette = themePalette,
                backgroundMode = settings.widgetBackgroundMode,
            )
            val darkTheme = settings.appVibe.isDark(systemInDarkMode)
            val buttonBackground = if (darkTheme) R.drawable.widget_button_dark else R.drawable.widget_button_light
            val cardBackground = if (darkTheme) R.drawable.widget_card_dark else R.drawable.widget_card_light
            LocalContext.current

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(palette.background))
                    .padding(metrics.outerPadding),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(end = 12.dp)
                            .clickable(actionRunCallback<OpenAppAction>()),
                    ) {
                        Text(
                            text = widgetFeedTitle,
                            style = TextStyle(
                                color = ColorProvider(palette.header),
                                fontWeight = FontWeight.Bold,
                                fontSize = type.header,
                            ),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                        ) {
                            val healthy = state.runtime.runtimeEnabled &&
                                state.runtime.lastFetchStatus != FetchStatus.Failed
                            StatusDot(healthy = healthy, palette = palette)
                            Text(
                                text = widgetMetaLine(
                                    storyCount = allWidgetStories.size,
                                    layoutMode = settings.widgetLayoutMode,
                                    runtimeEnabled = state.runtime.runtimeEnabled,
                                    fetchEnabled = state.runtime.fetchEnabled,
                                    status = if (fetching) {
                                        FetchStatus.Fetching
                                    } else if (state.runtime.lastFetchStatus == FetchStatus.Fetching) {
                                        FetchStatus.Idle
                                    } else {
                                        state.runtime.lastFetchStatus
                                    },
                                    showClock = showStatusText,
                                    tokensToday = tokensToday,
                                ),
                                style = TextStyle(
                                    color = ColorProvider(if (healthy) palette.muted else palette.warningText),
                                    fontSize = type.meta,
                                ),
                                maxLines = 1,
                            )
                        }
                    }

                    WidgetIconButton(
                        iconRes = R.drawable.ic_power,
                        contentDescription = if (state.runtime.runtimeEnabled) "Power off" else "Power on",
                        action = actionRunCallback<ToggleRuntimeAction>(),
                        backgroundRes = buttonBackground,
                        tint = if (state.runtime.runtimeEnabled) palette.statusText else palette.warningText,
                    )
                    if (fetching) {
                        Box(
                            modifier = GlanceModifier
                                .padding(end = 8.dp)
                                .background(ImageProvider(buttonBackground))
                                .cornerRadius(12.dp)
                                .clickable(actionRunCallback<RefreshAction>())
                                .padding(7.dp),
                        ) {
                            CircularProgressIndicator(
                                color = ColorProvider(palette.statusText),
                                modifier = GlanceModifier.size(18.dp),
                            )
                        }
                    } else {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_refresh,
                            contentDescription = "Refresh",
                            action = actionRunCallback<RefreshAction>(),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                    }
                    WidgetIconButton(
                        iconRes = if (stackMode) R.drawable.ic_view_column else R.drawable.ic_view_stack,
                        contentDescription = if (stackMode) "Show column mode" else "Show stack mode",
                        action = actionRunCallback<ToggleWidgetLayoutAction>(),
                        backgroundRes = buttonBackground,
                        tint = palette.header,
                    )
                }

                Spacer(GlanceModifier.height(metrics.sectionGap))

                val storyRows = buildList {
                    var previousSource: String? = null
                    stories.forEach { story ->
                        val showDivider = !stackMode && previousSource != story.source
                        if (showDivider) {
                            previousSource = story.source
                        }
                        add(story to showDivider)
                    }
                }

                // A LazyColumn keeps every story on screen: a plain Column is capped at ten
                // children by the remote-views translation, which silently dropped later stories.
                LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                    items(storyRows.size + 1) { index ->
                        if (index == storyRows.size) {
                            // Widgets get no pull gesture from Android, so the end of the list
                            // carries the "more" control instead.
                            val hasMoreStored = allWidgetStories.size > storyRows.size
                            val footerAction = if (hasMoreStored) {
                                actionRunCallback<ShowMoreStoriesAction>()
                            } else {
                                actionRunCallback<RefreshAction>()
                            }
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .background(ImageProvider(cardBackground))
                                        .cornerRadius(12.dp)
                                        .clickable(footerAction)
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(
                                        text = when {
                                            fetching -> "Fetching new stories"
                                            hasMoreStored -> "Load more stories"
                                            else -> "Fetch new stories"
                                        },
                                        style = TextStyle(
                                            color = ColorProvider(palette.muted),
                                            fontSize = type.meta,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                                Spacer(GlanceModifier.height(metrics.cardGap))
                            }
                            return@items
                        }
                        val (story, showDivider) = storyRows[index]
                        WidgetStoryRow(
                            story = story,
                            showDivider = showDivider,
                            expanded = story.id == expandedStoryId,
                            isAlert = state.alertMatches.any { it.storyId == story.id },
                            thumbnail = cachedImages[story.id]
                                .takeIf { size.width >= 220.dp && size.height >= 150.dp },
                            actionStyle = when {
                                !showActions -> WidgetActionStyle.None
                                stackMode -> WidgetActionStyle.Full
                                size.width >= 260.dp -> WidgetActionStyle.Compact
                                else -> WidgetActionStyle.None
                            },
                            showSummary = stackMode &&
                                size.width >= 260.dp &&
                                size.height >= metrics.summaryHeightThreshold,
                            showExtraActions = size.width >= 300.dp,
                            buttonBackground = buttonBackground,
                            cardBackground = cardBackground,
                            palette = palette,
                            metrics = metrics,
                            type = type,
                        )
                    }
                }

                if (!stackMode && showActions) {
                    Spacer(GlanceModifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_unread,
                            contentDescription = if (unreadOnly) "Show every story" else "Show unread only",
                            action = actionRunCallback<ToggleUnreadOnlyAction>(),
                            backgroundRes = buttonBackground,
                            tint = if (unreadOnly) palette.statusText else palette.muted,
                            compact = true,
                        )
                        if (allWidgetStories.size > stories.size) {
                            WidgetIconButton(
                                iconRes = R.drawable.ic_expand_more,
                                contentDescription = "Show more stories",
                                action = actionRunCallback<ShowMoreStoriesAction>(),
                                backgroundRes = buttonBackground,
                                tint = palette.muted,
                            )
                        }
                        WidgetIconButton(
                            iconRes = R.drawable.ic_done_all,
                            contentDescription = "Mark everything read",
                            action = actionRunCallback<MarkAllReadAction>(),
                            backgroundRes = buttonBackground,
                            tint = palette.muted,
                            compact = true,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_settings,
                            contentDescription = "Widget settings",
                            action = actionRunCallback<OpenWidgetSettingsAction>(),
                            backgroundRes = buttonBackground,
                            tint = palette.muted,
                            compact = true,
                        )
                        if (fetching) {
                            CircularProgressIndicator(
                                color = ColorProvider(palette.statusText),
                                modifier = GlanceModifier.size(14.dp),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                fetching -> "Fetching new stories"
                                unreadOnly -> "${stories.size} unread of ${allWidgetStories.size}"
                                else -> "${stories.size} of ${allWidgetStories.size}"
                            },
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = type.meta,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                if (stackMode && allWidgetStories.size > 1) {
                    Row(horizontalAlignment = Alignment.CenterHorizontally) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_arrow_up,
                            contentDescription = "Previous story",
                            action = actionRunCallback<PreviousStackStoryAction>(),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                        Text(
                            text = "${stackIndex + 1}/${allWidgetStories.size}",
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = type.meta,
                            ),
                            maxLines = 1,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_arrow_down,
                            contentDescription = "Next story",
                            action = actionRunCallback<NextStackStoryAction>(),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                    }
                }

                if (allWidgetStories.isEmpty()) {
                    Text(
                        text = "No visible stories",
                        style = TextStyle(
                            color = ColorProvider(palette.muted),
                            fontSize = type.meta,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetInstancePreferences(context).clear(appWidgetId)
        super.onDelete(context, glanceId)
    }
}

@androidx.compose.runtime.Composable
private fun WidgetStoryRow(
    story: NewsStory,
    showDivider: Boolean,
    expanded: Boolean,
    isAlert: Boolean,
    thumbnail: android.graphics.Bitmap?,
    actionStyle: WidgetActionStyle,
    showSummary: Boolean,
    showExtraActions: Boolean,
    buttonBackground: Int,
    cardBackground: Int,
    palette: WidgetPalette,
    metrics: WidgetMetrics,
    type: WidgetTypography,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (showDivider) {
            FeedDivider(
                feedName = story.source,
                palette = palette,
                type = type,
            )
        }
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(
                    if (isAlert) {
                        GlanceModifier.background(ColorProvider(palette.alertCard))
                    } else {
                        GlanceModifier.background(ImageProvider(cardBackground))
                    },
                )
                .cornerRadius(12.dp)
                .clickable(
                    actionRunCallback<OpenStoryAction>(
                        actionParametersOf(storyIdKey to story.id),
                    ),
                )
                .padding(metrics.cardPadding),
        ) {
            if (thumbnail == null) {
                StoryTextBlock(
                    story = story,
                    isAlert = isAlert,
                    showSummary = showSummary || expanded,
                    expanded = expanded,
                    palette = palette,
                    type = type,
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
                        story = story,
                        isAlert = isAlert,
                        showSummary = showSummary || expanded,
                        expanded = expanded,
                        palette = palette,
                        type = type,
                    )
                }
            }
            if (actionStyle == WidgetActionStyle.Compact) {
                Spacer(GlanceModifier.height(6.dp))
                Row(horizontalAlignment = Alignment.End, modifier = GlanceModifier.fillMaxWidth()) {
                    WidgetIconButton(
                        iconRes = R.drawable.ic_bookmark,
                        contentDescription = if (story.isSaved) "Remove from library" else "Save story",
                        action = actionRunCallback<ToggleSaveStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if (story.isSaved) palette.pinText else palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_share,
                        contentDescription = "Share story",
                        action = actionRunCallback<ShareStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_hide,
                        contentDescription = "Hide story",
                        action = actionRunCallback<HideStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = if (expanded) R.drawable.ic_collapse_less else R.drawable.ic_expand_more,
                        contentDescription = if (expanded) "Show less" else "Show the whole story",
                        action = actionRunCallback<ToggleExpandStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if (expanded) palette.statusText else palette.muted,
                        compact = true,
                    )
                }
            }
            if (actionStyle == WidgetActionStyle.Full) {
                Row(horizontalAlignment = Alignment.Start) {
                    WidgetIconButton(
                        iconRes = R.drawable.ic_open,
                        contentDescription = "Open source",
                        action = actionRunCallback<OpenStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.header,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_pin,
                        contentDescription = if (story.isPinned) "Unpin story" else "Pin story",
                        action = actionRunCallback<TogglePinStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if ("TogglePinStoryAction" == "TogglePinStoryAction" && story.isPinned) palette.pinText else palette.header,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_hide,
                        contentDescription = "Hide story",
                        action = actionRunCallback<HideStoryAction>(
                            actionParametersOf(storyIdKey to story.id),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if ("HideStoryAction" == "TogglePinStoryAction" && story.isPinned) palette.pinText else palette.header,
                    )
                    if (showExtraActions) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_copy,
                            contentDescription = "Copy link",
                            action = actionRunCallback<CopyStoryLinkAction>(
                                actionParametersOf(storyIdKey to story.id),
                            ),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_share,
                            contentDescription = "Share story",
                            action = actionRunCallback<ShareStoryAction>(
                                actionParametersOf(storyIdKey to story.id),
                            ),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                    }
                }
                Row(horizontalAlignment = Alignment.Start) {
                    WidgetIconButton(
                        iconRes = R.drawable.ic_summary,
                        contentDescription = "Open summary",
                        action = actionRunCallback<OpenStorySectionAction>(
                            actionParametersOf(
                                storyIdKey to story.id,
                                storySectionKey to StoryDetailSection.Summary.name,
                            ),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.header,
                    )
                    if (!story.research.isNullOrBlank()) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_research,
                            contentDescription = "Open research",
                            action = actionRunCallback<OpenStorySectionAction>(
                                actionParametersOf(
                                    storyIdKey to story.id,
                                    storySectionKey to StoryDetailSection.Research.name,
                                ),
                            ),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                    }
                    if (!story.translation.isNullOrBlank()) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_translation,
                            contentDescription = "Open translation",
                            action = actionRunCallback<OpenStorySectionAction>(
                                actionParametersOf(
                                    storyIdKey to story.id,
                                    storySectionKey to StoryDetailSection.Translation.name,
                                ),
                            ),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(metrics.cardGap))
    }
}

@androidx.compose.runtime.Composable
private fun StatusDot(
    healthy: Boolean,
    palette: WidgetPalette,
) {
    Box(
        modifier = GlanceModifier
            .padding(end = 7.dp)
            .size(9.dp)
            .background(ColorProvider(if (healthy) palette.statusPill else palette.warningPill))
            .cornerRadius(3.dp),
    ) {}
}

@androidx.compose.runtime.Composable
private fun FeedDivider(
    feedName: String,
    palette: WidgetPalette,
    type: WidgetTypography,
) {
    Text(
        text = feedName.uppercase(Locale.getDefault()),
        style = TextStyle(
            color = ColorProvider(palette.feedLabel),
            fontWeight = FontWeight.Bold,
            fontSize = type.meta,
        ),
        maxLines = 1,
    )
    Spacer(GlanceModifier.height(5.dp))
}

@androidx.compose.runtime.Composable
private fun StoryTextBlock(
    story: NewsStory,
    isAlert: Boolean,
    showSummary: Boolean,
    expanded: Boolean,
    palette: WidgetPalette,
    type: WidgetTypography,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = story.widgetTitle(),
            style = TextStyle(
                color = ColorProvider(
                    when {
                        isAlert -> palette.alertTitle
                        story.isRead -> palette.muted
                        else -> palette.storyTitle
                    },
                ),
                fontWeight = if (story.isRead) FontWeight.Normal else FontWeight.Bold,
                fontSize = type.title,
            ),
            maxLines = if (expanded) 6 else 2,
        )
        Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                isAlert -> WidgetBadge("ALERT", palette.warningPill, palette.warningText, type)
                story.isPinned -> WidgetBadge("PINNED", palette.pinPill, palette.pinText, type)
                story.isNew -> WidgetBadge("NEW", palette.statusPill, palette.statusText, type)
                else -> {}
            }
            Text(
                text = story.widgetSourceLine(),
                style = TextStyle(
                    color = ColorProvider(palette.muted),
                    fontSize = type.meta,
                ),
                maxLines = 1,
            )
        }
        if (showSummary) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = story.widgetSummary(),
                style = TextStyle(
                    color = ColorProvider(palette.body),
                    fontSize = type.body,
                ),
                maxLines = if (expanded) 10 else 3,
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetBadge(
    text: String,
    background: Color,
    foreground: Color,
    type: WidgetTypography,
) {
    Box(
        modifier = GlanceModifier
            .background(ColorProvider(background))
            .cornerRadius(4.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(foreground),
                fontWeight = FontWeight.Bold,
                fontSize = type.meta,
            ),
            maxLines = 1,
        )
    }
    Spacer(GlanceModifier.width(6.dp))
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
    val statusPill: Color,
    val statusText: Color,
    val warningPill: Color,
    val warningText: Color,
    val feedLabel: Color,
    val pinPill: Color,
    val pinText: Color,
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

private data class WidgetTypography(
    val header: TextUnit,
    val title: TextUnit,
    val body: TextUnit,
    val meta: TextUnit,
)

private fun widgetMetrics(densityMode: WidgetDensityMode): WidgetMetrics =
    when (densityMode) {
        WidgetDensityMode.Comfortable -> WidgetMetrics(
            outerPadding = 14.dp,
            cardPadding = 10.dp,
            cardGap = 8.dp,
            sectionGap = 10.dp,
            thumbnailSize = 58.dp,
            thumbnailGap = 8.dp,
            summaryHeightThreshold = 180.dp,
            extraStoryCapacity = 0,
        )

        WidgetDensityMode.Compact -> WidgetMetrics(
            outerPadding = 10.dp,
            cardPadding = 8.dp,
            cardGap = 6.dp,
            sectionGap = 7.dp,
            thumbnailSize = 46.dp,
            thumbnailGap = 6.dp,
            summaryHeightThreshold = 260.dp,
            extraStoryCapacity = 1,
        )
    }

private fun widgetTypography(
    typographyMode: WidgetTypographyMode,
    fontScale: FontScale,
): WidgetTypography {
    val base = when (typographyMode) {
        WidgetTypographyMode.Standard -> WidgetTypography(
            header = 15.sp,
            title = 15.sp,
            body = 13.sp,
            meta = 12.sp,
        )

        WidgetTypographyMode.Large -> WidgetTypography(
            header = 17.sp,
            title = 17.sp,
            body = 15.sp,
            meta = 13.sp,
        )
    }
    val scale = fontScale.scale
    return WidgetTypography(
        header = (base.header.value * scale).sp,
        title = (base.title.value * scale).sp,
        body = (base.body.value * scale).sp,
        meta = (base.meta.value * scale).sp,
    )
}

private fun normalizedStackIndex(stackIndex: Int, storyCount: Int): Int {
    if (storyCount == 0) return 0
    return ((stackIndex % storyCount) + storyCount) % storyCount
}

private fun widgetPalette(
    palette: ThemePalette,
    backgroundMode: WidgetBackgroundMode,
): WidgetPalette {
    val background = when (backgroundMode) {
        WidgetBackgroundMode.Solid -> palette.background
        WidgetBackgroundMode.Transparent -> (palette.background and 0x00FFFFFF) or (0xDDL shl 24)
    }
    return WidgetPalette(
        background = Color(background),
        card = Color(palette.panel),
        alertCard = Color(palette.alertPanel),
        header = Color(palette.textPrimary),
        storyTitle = Color(palette.textPrimary),
        alertTitle = Color(palette.accentRose),
        body = Color(palette.textSecondary),
        muted = Color(palette.textMuted),
        statusPill = Color(palette.successPanel),
        statusText = Color(palette.accentCyan),
        warningPill = Color(palette.alertPanel),
        warningText = Color(palette.accentRose),
        feedLabel = Color(palette.textMuted),
        pinPill = Color(palette.goldPanel),
        pinText = Color(palette.accentGold),
    )
}

private fun widgetMetaLine(
    storyCount: Int,
    layoutMode: WidgetLayoutMode,
    runtimeEnabled: Boolean,
    fetchEnabled: Boolean,
    status: FetchStatus,
    showClock: Boolean,
    tokensToday: Int,
): String {
    val state = when {
        !runtimeEnabled -> "Paused"
        !fetchEnabled -> "Fetch off"
        status == FetchStatus.Fetching -> "Updating"
        status == FetchStatus.Failed -> "Update failed"
        else -> "Updated ${formatWidgetClock()}"
    }
    val stories = if (storyCount == 1) "1 story" else "$storyCount stories"
    val mode = if (layoutMode == WidgetLayoutMode.Column) "" else " · one at a time"
    val tokens = if (tokensToday > 0) " · ${formatTokenCount(tokensToday)} tokens" else ""
    return if (showClock) "$state · $stories$mode$tokens" else "$stories$mode$tokens"
}

private fun formatTokenCount(tokens: Int): String =
    if (tokens >= 1000) "${"%.1f".format(tokens / 1000.0)}k" else tokens.toString()

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

enum class WidgetActionStyle {
    None,
    Compact,
    Full,
}

class ToggleExpandStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetInstancePreferences(context).toggleExpandedStory(appWidgetId, storyId)
        NewsWidget().updateAll(context)
    }
}

class ToggleUnreadOnlyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val preferences = WidgetInstancePreferences(context)
        preferences.saveUnreadOnly(appWidgetId, !preferences.unreadOnly(appWidgetId))
        NewsWidget().updateAll(context)
    }
}

class ShowMoreStoriesAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val preferences = WidgetInstancePreferences(context)
        preferences.saveStoryCount(appWidgetId, preferences.storyCount(appWidgetId) + 10)
        NewsWidget().updateAll(context)
    }
}

class MarkAllReadAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.markAllRead()
        NewsWidget().updateAll(context)
    }
}

class ToggleSaveStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        parameters[storyIdKey]?.let(NewsRepository::toggleSaved)
        NewsWidget().updateAll(context)
    }
}

class OpenWidgetSettingsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = Intent(context, NewsWidgetConfigureActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.startActivity(openAppIntent(context))
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        coroutineScope {
            val refresh = launch { NewsRepository.refreshNow() }
            // Paint the spinner straight away instead of only showing the result.
            delay(150)
            NewsWidget().updateAll(context)
            refresh.join()
        }
        NewsWidget().updateAll(context)
    }
}

class ToggleWidgetLayoutAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val preferences = WidgetInstancePreferences(context)
        val presetLayout = preferences.presetId(appWidgetId)
            ?.let { presetId -> NewsRepository.state.value.settings.widgetPresets.firstOrNull { it.id == presetId } }
            ?.layoutMode
        val current = preferences.layoutMode(appWidgetId)
            ?.let { runCatching { WidgetLayoutMode.valueOf(it) }.getOrNull() }
            ?: presetLayout
            ?: NewsRepository.state.value.settings.widgetLayoutMode
        val next = if (current == WidgetLayoutMode.Stack) WidgetLayoutMode.Column else WidgetLayoutMode.Stack
        preferences.saveLayoutMode(appWidgetId, next.name)
        NewsWidget().updateAll(context)
    }
}

class ToggleWidgetStoryCountAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val preferences = WidgetInstancePreferences(context)
        val nextCount = if (preferences.storyCount(appWidgetId) >= 40) 15 else 40
        preferences.saveStoryCount(appWidgetId, nextCount)
        NewsWidget().updateAll(context)
    }
}

class PreviousStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetInstancePreferences(context).moveStack(appWidgetId, -1)
        NewsWidget().updateAll(context)
    }
}

class NextStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetInstancePreferences(context).moveStack(appWidgetId, 1)
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

class TogglePinStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        parameters[storyIdKey]?.let(NewsRepository::togglePinned)
        NewsWidget().updateAll(context)
    }
}

class OpenStorySectionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val section = parameters[storySectionKey] ?: StoryDetailSection.Story.name
        val intent = openAppIntent(context)
            .putExtra(MainActivity.EXTRA_STORY_ID, storyId)
            .putExtra(MainActivity.EXTRA_STORY_SECTION, section)
        context.startActivity(intent)
    }
}

class OpenStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val story = NewsRepository.state.value.stories.firstOrNull { it.id == storyId } ?: return
        NewsRepository.markRead(storyId)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(story.sourceUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        NewsWidget().updateAll(context)
    }
}

class CopyStoryLinkAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val story = NewsRepository.state.value.stories.firstOrNull { it.id == storyId } ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(story.title, story.sourceUrl))
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Story link copied", Toast.LENGTH_SHORT).show()
        }
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
private val storySectionKey = ActionParameters.Key<String>("story-section")

private fun NewsStory.widgetTitle(): String =
    neutralTitle?.takeIf { it.isNotBlank() } ?: title

private fun NewsStory.widgetSourceLine(): String =
    listOfNotNull(
        formatWidgetTime(publishedAt),
        topicLabels.take(2).joinToString(", ").takeIf { it.isNotBlank() },
        "AI".takeIf { aiFieldsAvailable },
    ).joinToString(" · ")

private fun NewsStory.widgetSummary(): String =
    research?.takeIf { it.isNotBlank() }
        ?: translation?.takeIf { it.isNotBlank() }
        ?: summary

private fun formatWidgetClock(): String =
    LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

private fun formatWidgetTime(epochMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()))
    }.getOrDefault("")
