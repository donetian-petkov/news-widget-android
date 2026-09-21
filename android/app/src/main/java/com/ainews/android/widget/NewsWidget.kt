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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Per-instance view state lives in Glance's own state: Glance does not watch
// SharedPreferences, so writes there never triggered a recomposition.
private val pageIndexKey = intPreferencesKey("page-index")
private val settlingUntilKey = longPreferencesKey("settling-until")
private val unreadOnlyKey = booleanPreferencesKey("unread-only")
private val expandedStoryKey = stringPreferencesKey("expanded-story")
private val storyCountKey = intPreferencesKey("story-count")
private val layoutModeKey = stringPreferencesKey("layout-mode")
private val stackIndexKey = intPreferencesKey("stack-index")

private const val WIDGET_IMAGE_TIMEOUT_MILLIS = 9_000L
private const val WIDGET_PAGE_SIZE = 10

/** How many story cards fit in a widget of this height without scrolling. */
internal fun fittingRowCount(heightDp: Float, densityMode: WidgetDensityMode, fontScale: Float): Int {
    val rowHeight = when (densityMode) {
        WidgetDensityMode.Compact -> 92f
        WidgetDensityMode.Comfortable -> 108f
    } * fontScale
    val chrome = 156f
    return (((heightDp - chrome) / rowHeight).toInt()).coerceIn(1, 8)
}
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
        val storedState = runCatching {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        }.getOrNull()
        val visibleForImages = run {
            val state = NewsRepository.state.value
            val page = storedState?.get(pageIndexKey) ?: 0
            val unreadOnlyNow = storedState?.get(unreadOnlyKey) ?: false
            state.prioritizedStories
                .filter { !unreadOnlyNow || !it.isRead }
                .drop(page * WIDGET_PAGE_SIZE)
                .take(WIDGET_PAGE_SIZE)
        }
        // Only the stories about to be drawn get an image, fetched together when the widget
        // updates - which is exactly on refresh, load more and load previous.
        val wanted = visibleForImages.mapNotNull { story -> story.imageUrl?.let { story.id to it } }
        val cachedImages = coroutineScope {
            wanted.map { (id, url) ->
                async(Dispatchers.IO) {
                    val bitmap = imageDiskCache.loadCachedThumbnail(context, url)
                        ?: withTimeoutOrNull(WIDGET_IMAGE_TIMEOUT_MILLIS) {
                            runCatching { imageDiskCache.loadOrFetchThumbnail(context, url) }.getOrNull()
                        }
                    bitmap?.let { id to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        val missing = wanted.filterNot { (id, _) -> cachedImages.containsKey(id) }
        if (missing.isNotEmpty()) {
            // A slow image must not hold up the whole widget, so the stragglers finish in
            // the background and the widget is drawn again when they land.
            android.util.Log.d("AiNewsWidget", "still fetching ${missing.size} thumbnails")
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                missing.forEach { (_, url) ->
                    runCatching { imageDiskCache.loadOrFetchThumbnail(context, url) }
                }
                NewsWidget().update(context, id)
            }
        }
        android.util.Log.d(
            "AiNewsWidget",
            "page images: ${visibleForImages.count { it.imageUrl != null }} of " +
                "${visibleForImages.size} stories have urls, ${cachedImages.size} drawn",
        )

        provideContent {
            val widgetState = currentState<Preferences>()
            val state = NewsRepository.state.value
            val preset = instancePreferences.presetId(appWidgetId)
                ?.let { presetId -> state.settings.widgetPresets.firstOrNull { it.id == presetId } }
            val instanceLayoutMode = (widgetState[layoutModeKey] ?: instancePreferences.layoutMode(appWidgetId))
                ?.let { modeName -> runCatching { WidgetLayoutMode.valueOf(modeName) }.getOrNull() }
            val settings = state.settings.copy(
                widgetFeedSourceId = preset?.feedSourceId ?: state.settings.widgetFeedSourceId,
                widgetFeedSourceIds = preset?.effectiveFeedSourceIds() ?: state.settings.effectiveWidgetFeedSourceIds(),
                widgetLayoutMode = instanceLayoutMode ?: preset?.layoutMode ?: state.settings.widgetLayoutMode,
                widgetBackgroundMode = preset?.backgroundMode ?: state.settings.widgetBackgroundMode,
                appVibe = preset?.vibe ?: state.settings.appVibe,
                widgetDensityMode = preset?.densityMode ?: state.settings.widgetDensityMode,
                widgetTypographyMode = preset?.typographyMode ?: state.settings.widgetTypographyMode,
                widgetStackIndex = widgetState[stackIndexKey] ?: instancePreferences.stackIndex(appWidgetId),
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
            }.filter { story -> !(widgetState[unreadOnlyKey] ?: false) || !story.isRead }
            val size = LocalSize.current
            val metrics = widgetMetrics(settings.widgetDensityMode)
            val type = widgetTypography(settings.widgetTypographyMode, settings.widgetFontScale)
            val pageSize = WIDGET_PAGE_SIZE
            val unreadOnly = widgetState[unreadOnlyKey] ?: instancePreferences.unreadOnly(appWidgetId)
            val expandedStoryId = widgetState[expandedStoryKey] ?: instancePreferences.expandedStoryId(appWidgetId)
            val tokensToday = state.usageRecords
                .filter { it.createdAt > System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
                .sumOf { it.tokens }
            val pageCount = if (allWidgetStories.isEmpty()) {
                1
            } else {
                ((allWidgetStories.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            }
            val pageIndex = (widgetState[pageIndexKey] ?: 0).coerceIn(0, pageCount - 1)
            val firstStoryNumber = pageIndex * pageSize + 1
            val hasPrevious = pageIndex > 0
            val hasNext = pageIndex < pageCount - 1
            // Rendering one row first clamps the list back to the top; the full page follows
            // in the same update pass. Widgets give no way to scroll a list directly.
            // A deadline rather than a flag: if the action that set it is killed before it
            // can clear it, the loading frame expires by itself instead of sticking.
            val settling = System.currentTimeMillis() < (widgetState[settlingUntilKey] ?: 0L)
            // A widget update travels as one RemoteViews parcel, which is why the list has a
            // ceiling: past it rows come back empty.
            val storyLimit = pageSize
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
                allWidgetStories.drop(pageIndex * pageSize).take(storyLimit)
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
            val flatButtonBackground = if (darkTheme) {
                R.drawable.widget_button_flat_dark
            } else {
                R.drawable.widget_button_flat_light
            }
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

                if (settling && !stackMode) {
                    // The clamp frame: a short, deliberate loading line while the list is
                    // reset to the top, instead of flashing a single story.
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ColorProvider(palette.statusText),
                                modifier = GlanceModifier.size(16.dp),
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            Text(
                                text = "Loading stories $firstStoryNumber-" +
                                    "${minOf(firstStoryNumber + pageSize - 1, allWidgetStories.size)}",
                                style = TextStyle(
                                    color = ColorProvider(palette.muted),
                                    fontSize = type.meta,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    return@Column
                }

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
                    items(storyRows.size + 2) { index ->
                        if (index == 0) {
                            if (stackMode || !hasPrevious) return@items
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .background(ImageProvider(cardBackground))
                                        .cornerRadius(12.dp)
                                        .clickable(
                                            actionRunCallback<PreviousPageAction>(
                                                actionParametersOf(appWidgetIdKey to appWidgetId),
                                            ),
                                        )
                                        .padding(vertical = 12.dp),
                                ) {
                                    Text(
                                        text = "Load previous $pageSize",
                                        style = TextStyle(
                                            color = ColorProvider(palette.statusText),
                                            fontSize = type.meta,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                                Spacer(GlanceModifier.height(metrics.cardGap))
                            }
                            return@items
                        }
                        if (index == storyRows.size + 1) {
                            if (stackMode) return@items
                            val hasNextPage = hasNext
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .background(ImageProvider(cardBackground))
                                        .cornerRadius(12.dp)
                                        .clickable(
                                            if (hasNextPage) {
                                                actionRunCallback<NextPageAction>(
                                                    actionParametersOf(appWidgetIdKey to appWidgetId),
                                                )
                                            } else {
                                                actionRunCallback<RefreshAction>(
                                                    actionParametersOf(appWidgetIdKey to appWidgetId),
                                                )
                                            },
                                        )
                                        .padding(vertical = 12.dp),
                                ) {
                                    if (fetching) {
                                        CircularProgressIndicator(
                                            color = ColorProvider(palette.statusText),
                                            modifier = GlanceModifier.size(14.dp),
                                        )
                                        Spacer(GlanceModifier.width(8.dp))
                                    }
                                    Text(
                                        text = when {
                                            fetching -> "Fetching new stories"
                                            hasNextPage -> "Load next $pageSize stories"
                                            else -> "Fetch new stories"
                                        },
                                        style = TextStyle(
                                            color = ColorProvider(palette.statusText),
                                            fontSize = type.meta,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        maxLines = 1,
                                    )
                                }
                                Spacer(GlanceModifier.height(metrics.cardGap))
                            }
                            return@items
                        }
                        val (story, showDivider) = storyRows[index - 1]
                        WidgetStoryRow(
                            story = story,
                            widgetId = appWidgetId,
                            showDivider = showDivider,
                            expanded = expandedStoryId.orEmpty().isNotEmpty() && story.id == expandedStoryId,
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
                            buttonBackground = flatButtonBackground,
                            cardBackground = cardBackground,
                            palette = palette,
                            metrics = metrics,
                            type = type,
                        )
                    }
                }

                if (!stackMode && showActions) {
                    Spacer(GlanceModifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                fetching -> "Fetching"
                                stories.isEmpty() -> "Nothing to show"
                                else -> "$firstStoryNumber-${firstStoryNumber + stories.size - 1} " +
                                    "of ${allWidgetStories.size}"
                            },
                            style = TextStyle(
                                color = ColorProvider(palette.muted),
                                fontSize = type.meta,
                            ),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        if (hasPrevious) {
                            WidgetIconButton(
                                iconRes = R.drawable.ic_arrow_up,
                                contentDescription = "Back to the newest stories",
                                action = actionRunCallback<ResetPageAction>(actionParametersOf(appWidgetIdKey to appWidgetId)),
                                backgroundRes = flatButtonBackground,
                                tint = palette.statusText,
                                compact = true,
                            )
                        }
                        WidgetIconButton(
                            iconRes = R.drawable.ic_unread,
                            contentDescription = if (unreadOnly) "Show every story" else "Show unread only",
                            action = actionRunCallback<ToggleUnreadOnlyAction>(actionParametersOf(appWidgetIdKey to appWidgetId)),
                            backgroundRes = flatButtonBackground,
                            tint = if (unreadOnly) palette.statusText else palette.muted,
                            compact = true,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_done_all,
                            contentDescription = "Mark everything read",
                            action = actionRunCallback<MarkAllReadAction>(actionParametersOf(appWidgetIdKey to appWidgetId)),
                            backgroundRes = flatButtonBackground,
                            tint = palette.muted,
                            compact = true,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_settings,
                            contentDescription = "Widget settings",
                            action = actionRunCallback<OpenWidgetSettingsAction>(actionParametersOf(appWidgetIdKey to appWidgetId)),
                            backgroundRes = flatButtonBackground,
                            tint = palette.muted,
                            compact = true,
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
    widgetId: Int,
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
            if (thumbnail == null && story.imageUrl == null) {
                StoryTextBlock(
                    story = story,
                    widgetId = widgetId,
                    isAlert = isAlert,
                    showSummary = showSummary || expanded,
                    expanded = expanded,
                    palette = palette,
                    type = type,
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    if (thumbnail != null) {
                        Image(
                            provider = ImageProvider(thumbnail),
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier
                                .size(metrics.thumbnailSize)
                                .cornerRadius(6.dp),
                        )
                    } else {
                        // Keeps every card the same shape while an image is still coming.
                        Box(
                            modifier = GlanceModifier
                                .size(metrics.thumbnailSize)
                                .background(ColorProvider(palette.card))
                                .cornerRadius(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = story.source.take(1).uppercase(Locale.getDefault()),
                                style = TextStyle(
                                    color = ColorProvider(palette.muted),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = type.title,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(GlanceModifier.width(metrics.thumbnailGap))
                    StoryTextBlock(
                        story = story,
                        widgetId = widgetId,
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
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if (story.isSaved) palette.pinText else palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_share,
                        contentDescription = "Share story",
                        action = actionRunCallback<ShareStoryAction>(
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_hide,
                        contentDescription = "Hide story",
                        action = actionRunCallback<HideStoryAction>(
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.muted,
                        compact = true,
                    )
                    WidgetIconButton(
                        iconRes = if (expanded) R.drawable.ic_collapse_less else R.drawable.ic_expand_more,
                        contentDescription = if (expanded) "Show less" else "Show the whole story",
                        action = actionRunCallback<ToggleExpandStoryAction>(
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
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
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = palette.header,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_pin,
                        contentDescription = if (story.isPinned) "Unpin story" else "Pin story",
                        action = actionRunCallback<TogglePinStoryAction>(
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if ("TogglePinStoryAction" == "TogglePinStoryAction" && story.isPinned) palette.pinText else palette.header,
                    )
                    WidgetIconButton(
                        iconRes = R.drawable.ic_hide,
                        contentDescription = "Hide story",
                        action = actionRunCallback<HideStoryAction>(
                            actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                        ),
                        backgroundRes = buttonBackground,
                        tint = if ("HideStoryAction" == "TogglePinStoryAction" && story.isPinned) palette.pinText else palette.header,
                    )
                    if (showExtraActions) {
                        WidgetIconButton(
                            iconRes = R.drawable.ic_copy,
                            contentDescription = "Copy link",
                            action = actionRunCallback<CopyStoryLinkAction>(
                                actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
                            ),
                            backgroundRes = buttonBackground,
                            tint = palette.header,
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_share,
                            contentDescription = "Share story",
                            action = actionRunCallback<ShareStoryAction>(
                                actionParametersOf(storyIdKey to story.id, appWidgetIdKey to widgetId),
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
    widgetId: Int,
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

/** Moves a page and lands on the first story of it, by clamping the list to the top first. */
private suspend fun turnPage(context: Context, appWidgetId: Int, glanceId: GlanceId, forward: Boolean) {
    editWidgetState(context, appWidgetId, glanceId) { prefs ->
        val current = prefs[pageIndexKey] ?: 0
        prefs[pageIndexKey] = (if (forward) current + 1 else current - 1).coerceAtLeast(0)
        prefs[settlingUntilKey] = System.currentTimeMillis() + SETTLE_MILLIS
    }
    settle(context, appWidgetId, glanceId)
}

private const val SETTLE_MILLIS = 450L

private suspend fun settle(context: Context, appWidgetId: Int, glanceId: GlanceId) {
    delay(SETTLE_MILLIS)
    editWidgetState(context, appWidgetId, glanceId) { prefs -> prefs[settlingUntilKey] = 0L }
}

class PreviousPageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        turnPage(context, parameters.widgetId(context, glanceId), glanceId, forward = false)
    }
}

class NextPageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        turnPage(context, appWidgetId, glanceId, forward = true)
    }
}

class ResetPageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            prefs[pageIndexKey] = 0
            prefs[settlingUntilKey] = System.currentTimeMillis() + SETTLE_MILLIS
        }
        settle(context, appWidgetId, glanceId)
    }
}

class ToggleExpandStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val storyId = parameters[storyIdKey] ?: return
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            val next = if (prefs[expandedStoryKey] == storyId) "" else storyId
            prefs[expandedStoryKey] = next
            WidgetInstancePreferences(context).toggleExpandedStory(appWidgetId, storyId)
        }
    }
}

class ToggleUnreadOnlyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            val next = !(prefs[unreadOnlyKey] ?: false)
            prefs[unreadOnlyKey] = next
            prefs[pageIndexKey] = 0
            WidgetInstancePreferences(context).saveUnreadOnly(appWidgetId, next)
        }
    }
}

class ShowMoreStoriesAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            val next = ((prefs[storyCountKey] ?: 12) + 4).coerceAtMost(20)
            prefs[storyCountKey] = next
            WidgetInstancePreferences(context).saveStoryCount(appWidgetId, next)
        }
    }
}

class MarkAllReadAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        NewsRepository.markAllRead()
        refreshWidget(context, parameters.widgetId(context, glanceId), glanceId)
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
        refreshWidget(context, parameters.widgetId(context, glanceId), glanceId)
        NewsWidget().updateAll(context)
    }
}

class OpenWidgetSettingsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
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
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            prefs[pageIndexKey] = 0
            prefs[settlingUntilKey] = 0L
        }
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
        val appWidgetId = parameters.widgetId(context, glanceId)
        val preferences = WidgetInstancePreferences(context)
        val presetLayout = preferences.presetId(appWidgetId)
            ?.let { presetId -> NewsRepository.state.value.settings.widgetPresets.firstOrNull { it.id == presetId } }
            ?.layoutMode
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            val current = (prefs[layoutModeKey] ?: preferences.layoutMode(appWidgetId))
                ?.let { runCatching { WidgetLayoutMode.valueOf(it) }.getOrNull() }
                ?: presetLayout
                ?: NewsRepository.state.value.settings.widgetLayoutMode
            val next = if (current == WidgetLayoutMode.Stack) WidgetLayoutMode.Column else WidgetLayoutMode.Stack
            prefs[layoutModeKey] = next.name
            prefs[pageIndexKey] = 0
            preferences.saveLayoutMode(appWidgetId, next.name)
        }
    }
}

class ToggleWidgetStoryCountAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            val next = when (prefs[storyCountKey] ?: 12) {
                in 0..7 -> 12
                in 8..14 -> 20
                else -> 6
            }
            prefs[storyCountKey] = next
            prefs[pageIndexKey] = 0
            WidgetInstancePreferences(context).saveStoryCount(appWidgetId, next)
        }
    }
}

class PreviousStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            prefs[stackIndexKey] = (prefs[stackIndexKey] ?: 0) - 1
            WidgetInstancePreferences(context).moveStack(appWidgetId, -1)
        }
    }
}

class NextStackStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters.widgetId(context, glanceId)
        editWidgetState(context, appWidgetId, glanceId) { prefs ->
            prefs[stackIndexKey] = (prefs[stackIndexKey] ?: 0) + 1
            WidgetInstancePreferences(context).moveStack(appWidgetId, 1)
        }
    }
}

class HideStoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        parameters[storyIdKey]?.let(NewsRepository::hideStory)
        refreshWidget(context, parameters.widgetId(context, glanceId), glanceId)
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
        refreshWidget(context, parameters.widgetId(context, glanceId), glanceId)
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
private val appWidgetIdKey = ActionParameters.Key<Int>("app-widget-id")

/** Writes this widget's view state and redraws it. */
private suspend fun editWidgetState(
    context: Context,
    appWidgetId: Int,
    glanceId: GlanceId,
    edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
) {
    val target = runCatching {
        GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    }.getOrDefault(glanceId)
    updateAppWidgetState(context, target) { prefs -> edit(prefs) }
    NewsWidget().update(context, target)
}

private suspend fun refreshWidget(context: Context, appWidgetId: Int, glanceId: GlanceId) {
    editWidgetState(context, appWidgetId, glanceId) { }
}

/** List-row actions cannot resolve their widget from the glance id, so it travels with them. */
private fun ActionParameters.widgetId(context: Context, glanceId: GlanceId): Int =
    this[appWidgetIdKey] ?: GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

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
