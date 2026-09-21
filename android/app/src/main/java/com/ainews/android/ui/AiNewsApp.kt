package com.ainews.android.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.ainews.android.R
import com.ainews.android.data.AiProvider
import com.ainews.android.data.BackendMode
import com.ainews.android.data.DigestEntry
import com.ainews.android.data.FeedFetchRecord
import com.ainews.android.data.FeedHealth
import com.ainews.android.data.FeedSource
import com.ainews.android.data.FeedViewMode
import com.ainews.android.data.FontScale
import com.ainews.android.data.FetchStatus
import com.ainews.android.data.NewsMonitor
import com.ainews.android.data.NewsSchedule
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.NewsStory
import com.ainews.android.data.NewsUiState
import com.ainews.android.data.RuntimeSettings
import com.ainews.android.data.ScheduleKind
import com.ainews.android.data.StoryAiAction
import com.ainews.android.data.StoryDetailSection
import com.ainews.android.data.WIDGET_ALL_FEEDS
import com.ainews.android.data.WIDGET_FILTERED_FEED
import com.ainews.android.data.WidgetBackgroundMode
import com.ainews.android.data.WidgetDensityMode
import com.ainews.android.data.WidgetLayoutMode
import com.ainews.android.data.WidgetPreset
import com.ainews.android.data.AppVibe
import com.ainews.android.data.WidgetTypographyMode
import com.ainews.android.data.aiBudgetText
import com.ainews.android.data.aiConfigured
import com.ainews.android.data.dynamicThemePalette
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
    var screen by remember { mutableStateOf(AppScreen.Feed) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BackHandler(enabled = drawerState.isOpen || screen != AppScreen.Feed || selectedStory != null) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectedStory != null -> NewsRepository.selectStory(null)
            else -> screen = AppScreen.Feed
        }
    }

    val systemInDarkMode = isSystemInDarkTheme()
    val palette = state.settings
        .takeIf { it.useMaterialYou }
        ?.let { dynamicThemePalette(context, state.settings.appVibe.isDark(systemInDarkMode)) }
        ?: state.settings.appVibe.palette(systemInDarkMode)
    val dynamicColours = state.settings.useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (dynamicColours) {
        if (state.settings.appVibe.isDark(systemInDarkMode)) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else if (state.settings.appVibe.isDark(systemInDarkMode)) {
        darkColorScheme(
            background = Color(palette.background),
            surface = Color(palette.panel),
            surfaceVariant = Color(palette.backgroundAlt),
            primary = Color(palette.accentBlue),
            secondary = Color(palette.accentCyan),
            tertiary = Color(palette.accentGold),
            error = Color(palette.accentRose),
            onSurface = Color(palette.textPrimary),
            onSurfaceVariant = Color(palette.textSecondary),
            onBackground = Color(palette.textPrimary),
            outlineVariant = Color(palette.chipPanel),
            outline = Color(palette.panelBorder),
        )
    } else {
        lightColorScheme(
            background = Color(palette.background),
            surface = Color(palette.panel),
            surfaceVariant = Color(palette.backgroundAlt),
            primary = Color(palette.accentBlue),
            secondary = Color(palette.accentCyan),
            tertiary = Color(palette.accentGold),
            error = Color(palette.accentRose),
            onSurface = Color(palette.textPrimary),
            onSurfaceVariant = Color(palette.textSecondary),
            onBackground = Color(palette.textPrimary),
            outlineVariant = Color(palette.chipPanel),
            outline = Color(palette.panelBorder),
        )
    }

    fun refreshWidget() {
        scope.launch { NewsWidget().updateAll(context) }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * state.settings.appFontScale.scale,
        ),
    ) {
    MaterialTheme(colorScheme = colorScheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = screen == AppScreen.Feed && selectedStory == null,
            drawerContent = {
                AppDrawer(
                    state = state,
                    current = screen,
                    onNavigate = { destination ->
                        screen = destination
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (selectedStory != null) {
                StoryDetail(
                    story = selectedStory,
                    section = state.selectedStorySection,
                    aiConfigured = state.settings.aiConfigured,
                    aiReady = state.settings.aiConfigured &&
                        state.runtime.runtimeEnabled &&
                        state.runtime.aiEnabled,
                    onBack = { NewsRepository.selectStory(null) },
                    onHide = {
                        NewsRepository.hideStory(selectedStory.id)
                        refreshWidget()
                    },
                    onTogglePin = {
                        NewsRepository.togglePinned(selectedStory.id)
                        refreshWidget()
                    },
                    onToggleSave = { NewsRepository.toggleSaved(selectedStory.id) },
                    onRunAction = { action ->
                        scope.launch {
                            NewsRepository.runStoryAction(selectedStory.id, action)
                            NewsWidget().updateAll(context)
                        }
                    },
                    onShare = { shareStory(context, selectedStory) },
                )
            } else {
                when (screen) {
                    AppScreen.Settings -> SettingsScreen(
                        settings = state.settings,
                        feedSources = state.feedSources,
                        onBack = { screen = AppScreen.Feed },
                        onSave = {
                            NewsRepository.updateSettings(it)
                            refreshWidget()
                        },
                        onSaveKey = NewsRepository::saveProviderKey,
                        onClearKey = NewsRepository::clearProviderKey,
                    )

                    AppScreen.Hidden -> HiddenStoriesScreen(
                        stories = state.stories.filter { it.isHidden },
                        onBack = { screen = AppScreen.Feed },
                        onRestoreStory = {
                            NewsRepository.restoreStory(it)
                            refreshWidget()
                        },
                        onRestoreAll = {
                            NewsRepository.restoreHidden()
                            refreshWidget()
                        },
                    )

                    AppScreen.Monitors -> MonitorScreen(
                        monitors = state.monitors,
                        onBack = { screen = AppScreen.Feed },
                        onAddMonitor = NewsRepository::addMonitor,
                        onToggleMonitor = NewsRepository::toggleMonitor,
                        onDeleteMonitor = NewsRepository::deleteMonitor,
                        onScanMonitors = {
                            NewsRepository.scanMonitorsNow()
                            refreshWidget()
                        },
                    )

                    AppScreen.Feeds -> FeedSourceScreen(
                        feedSources = state.feedSources,
                        onBack = { screen = AppScreen.Feed },
                        onAddFeed = NewsRepository::addFeedSource,
                        onDeleteFeed = NewsRepository::deleteFeedSource,
                        onResetFeeds = NewsRepository::resetFeedSources,
                        onToggleFetch = {
                            NewsRepository.toggleFeedFetch(it)
                            refreshWidget()
                        },
                        onToggleAi = NewsRepository::toggleFeedAi,
                        onToggleNeutralTitles = NewsRepository::toggleFeedNeutralTitles,
                        onSetAllFeeds = { fetch, ai, neutral ->
                            NewsRepository.setAllFeeds(fetch, ai, neutral)
                            refreshWidget()
                        },
                        onImportOpml = NewsRepository::importOpml,
                        onExportOpml = NewsRepository::exportOpml,
                        onRefresh = {
                            scope.launch {
                                NewsRepository.refreshNow()
                                NewsWidget().updateAll(context)
                            }
                        },
                    )

                    AppScreen.Library -> LibraryScreen(
                        stories = state.savedStories,
                        onBack = { screen = AppScreen.Feed },
                        onOpenStory = NewsRepository::selectStory,
                        onToggleSave = NewsRepository::toggleSaved,
                        onShareStory = { shareStory(context, it) },
                    )

                    AppScreen.Keywords -> KeywordScreen(
                        keywords = state.settings.keywords,
                        matches = state.keywordMatches,
                        onBack = { screen = AppScreen.Feed },
                        onAddKeyword = {
                            NewsRepository.addKeyword(it)
                            refreshWidget()
                        },
                        onRemoveKeyword = {
                            NewsRepository.removeKeyword(it)
                            refreshWidget()
                        },
                        onOpenStory = NewsRepository::selectStory,
                    )

                    AppScreen.History -> HistoryScreen(
                        history = state.fetchHistory,
                        onBack = { screen = AppScreen.Feed },
                        onClear = NewsRepository::clearFetchHistory,
                    )

                    AppScreen.Sources -> SourcesScreen(
                        health = state.feedHealth,
                        onBack = { screen = AppScreen.Feed },
                        onToggleFetch = {
                            NewsRepository.toggleFeedFetch(it)
                            refreshWidget()
                        },
                        onToggleAi = NewsRepository::toggleFeedAi,
                    )

                    AppScreen.Usage -> UsageScreen(
                        state = state,
                        onBack = { screen = AppScreen.Feed },
                        onReset = NewsRepository::resetUsage,
                        onToggleAi = {
                            NewsRepository.toggleAi()
                            refreshWidget()
                        },
                        onRegenerate = {
                            scope.launch {
                                NewsRepository.regenerateMissingAi()
                                NewsWidget().updateAll(context)
                            }
                        },
                        onRegenerateKind = { action ->
                            scope.launch {
                                NewsRepository.regenerateMissing(action)
                                NewsWidget().updateAll(context)
                            }
                        },
                    )

                    AppScreen.Digests -> DigestScreen(
                        digests = state.digests,
                        onBack = { screen = AppScreen.Feed },
                        onBuildDigest = { NewsRepository.buildDigestNow() },
                        onDeleteDigest = NewsRepository::deleteDigest,
                    )

                    AppScreen.Schedules -> ScheduleScreen(
                        schedules = state.schedules,
                        onBack = { screen = AppScreen.Feed },
                        onAddSchedule = { kind, hour -> NewsRepository.addSchedule(kind, hour) },
                        onToggleSchedule = NewsRepository::toggleSchedule,
                        onDeleteSchedule = NewsRepository::deleteSchedule,
                        onRunSchedule = {
                            scope.launch {
                                NewsRepository.runScheduleNow(it)
                                NewsWidget().updateAll(context)
                            }
                        },
                    )

                    AppScreen.Feed -> NewsFeed(
                        state = state,
                        onRefresh = {
                            scope.launch {
                                NewsRepository.refreshNow()
                                NewsWidget().updateAll(context)
                            }
                        },
                        onPower = {
                            NewsRepository.toggleRuntime()
                            refreshWidget()
                        },
                        onAi = {
                            NewsRepository.toggleAi()
                            refreshWidget()
                        },
                        onSetTimeout = {
                            NewsRepository.setAutoPowerOff(it)
                            refreshWidget()
                        },
                        onRestoreHidden = {
                            NewsRepository.restoreHidden()
                            refreshWidget()
                        },
                        onScanMonitors = {
                            NewsRepository.scanMonitorsNow()
                            refreshWidget()
                        },
                        onEnrich = {
                            scope.launch {
                                NewsRepository.enrichVisibleStories()
                                NewsWidget().updateAll(context)
                            }
                        },
                        onSelectTopic = NewsRepository::selectTopic,
                        onSelectViewMode = NewsRepository::setFeedViewMode,
                        onOpenScreen = { screen = it },
                        onDismissOnboarding = NewsRepository::dismissOnboarding,
                        onUseLocalAi = {
                            NewsRepository.useLocalAiProvider()
                            refreshWidget()
                        },
                        onOpenStory = NewsRepository::selectStory,
                        onHideStory = {
                            NewsRepository.hideStory(it)
                            refreshWidget()
                        },
                        onTogglePinStory = {
                            NewsRepository.togglePinned(it)
                            refreshWidget()
                        },
                        onToggleSaveStory = NewsRepository::toggleSaved,
                        onToggleReadStory = { storyId, read ->
                            NewsRepository.markRead(storyId, read)
                            refreshWidget()
                        },
                        onShareStory = { shareStory(context, it) },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = NewsRepository::setSearchQuery,
                        onMarkAllRead = {
                            NewsRepository.markAllRead()
                            refreshWidget()
                        },
                    )
                }
            }
        }
        }
    }
    }
}

private val LocalAppPalette = staticCompositionLocalOf { AppVibe.Light.palette(false) }

private enum class AppScreen {
    Feed,
    Settings,
    Hidden,
    Monitors,
    Feeds,
    Library,
    Keywords,
    History,
    Sources,
    Usage,
    Digests,
    Schedules,
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onSelectViewMode: (FeedViewMode) -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onDismissOnboarding: () -> Unit,
    onUseLocalAi: () -> Unit,
    onOpenStory: (String) -> Unit,
    onHideStory: (String) -> Unit,
    onTogglePinStory: (String) -> Unit,
    onToggleSaveStory: (String) -> Unit,
    onToggleReadStory: (String, Boolean) -> Unit,
    onShareStory: (NewsStory) -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val storyCount = state.prioritizedStories.size

    PullToRefreshBox(
        isRefreshing = state.runtime.lastFetchStatus == FetchStatus.Fetching,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
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
                onSelectViewMode = onSelectViewMode,
                onOpenScreen = onOpenScreen,
                onOpenDrawer = onOpenDrawer,
                onDismissOnboarding = onDismissOnboarding,
                onUseLocalAi = onUseLocalAi,
                onSearch = onSearch,
                onMarkAllRead = onMarkAllRead,
            )
        }

        items(state.prioritizedStories, key = { it.id }) { story ->
            val swipeState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            onToggleSaveStory(story.id)
                            false
                        }

                        SwipeToDismissBoxValue.EndToStart -> {
                            onHideStory(story.id)
                            true
                        }

                        SwipeToDismissBoxValue.Settled -> false
                    }
                },
            )
            SwipeToDismissBox(
                state = swipeState,
                backgroundContent = {
                    val saving = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                    Box(
                        contentAlignment = if (saving) Alignment.CenterStart else Alignment.CenterEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (saving) {
                                    Color(LocalAppPalette.current.successPanel)
                                } else {
                                    Color(LocalAppPalette.current.alertPanel)
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = if (saving) {
                                if (story.isSaved) "Unsave" else "Save"
                            } else {
                                "Hide"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (saving) {
                                Color(LocalAppPalette.current.accentCyan)
                            } else {
                                Color(LocalAppPalette.current.accentRose)
                            },
                        )
                    }
                },
            ) {
                StoryCard(
                    isAlert = state.alertMatches.any { it.storyId == story.id },
                    story = story,
                    onOpen = { onOpenStory(story.id) },
                    onHide = { onHideStory(story.id) },
                    onTogglePin = { onTogglePinStory(story.id) },
                    onToggleSave = { onToggleSaveStory(story.id) },
                    onToggleRead = { onToggleReadStory(story.id, !story.isRead) },
                    onShare = { onShareStory(story) },
                )
            }
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

        if (storyCount > 0) {
            item {
                val fetching = state.runtime.lastFetchStatus == FetchStatus.Fetching
                // Reaching the end asks the feeds for anything newer they are publishing.
                LaunchedEffect(storyCount) {
                    if (!fetching && state.runtime.runtimeEnabled) onRefresh()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                ) {
                    if (fetching) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Checking the feeds for more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(onClick = onRefresh, enabled = state.runtime.runtimeEnabled) {
                            Text("Check for more stories")
                        }
                        Text(
                            text = "$storyCount stories loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

        if (storyCount > 4) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = "Back to top",
                        modifier = Modifier.size(18.dp),
                    )
                }
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(storyCount) } },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = "Jump to the end",
                        modifier = Modifier.size(18.dp),
                    )
                }
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
    onSelectViewMode: (FeedViewMode) -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onOpenDrawer: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onUseLocalAi: () -> Unit,
    onSearch: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(state.searchQuery.isNotBlank()) }
    val onToggleSearch = {
        searchOpen = !searchOpen
        if (!searchOpen) onSearch("")
    }
    val hiddenCount = state.stories.count { it.isHidden }
    val aiConfigured = state.settings.aiConfigured
    val aiReady = aiConfigured && state.runtime.runtimeEnabled && state.runtime.aiEnabled

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "Open navigation",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = state.feedTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.settings.aiConfigured) {
                        "${state.runtime.statusText} - ${state.runtime.aiBudgetText}"
                    } else {
                        state.runtime.statusText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onPower) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = if (state.runtime.runtimeEnabled) "Turn runtime off" else "Turn runtime on",
                    tint = if (state.runtime.runtimeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onRefresh, enabled = state.runtime.runtimeEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    tint = if (state.runtime.runtimeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(onClick = { onToggleSearch() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = if (searchOpen) "Close search" else "Search stories",
                    tint = if (searchOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // The kebab holds actions only; screens live in the navigation drawer.
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (aiConfigured) {
                        DropdownMenuItem(
                            text = { Text(if (state.runtime.aiEnabled) "Pause AI" else "Resume AI") },
                            onClick = { menuOpen = false; onAi() },
                        )
                        DropdownMenuItem(
                            text = { Text("Fill missing AI (${state.pendingAiCount})") },
                            enabled = aiReady,
                            onClick = { menuOpen = false; onEnrich() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Set up AI") },
                            onClick = { menuOpen = false; onOpenScreen(AppScreen.Settings) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Scan monitors") },
                        onClick = { menuOpen = false; onScanMonitors() },
                    )
                    DropdownMenuItem(
                        text = { Text("Mark all read (${state.unreadCount})") },
                        enabled = state.unreadCount > 0,
                        onClick = { menuOpen = false; onMarkAllRead() },
                    )
                    if (hiddenCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Restore $hiddenCount hidden") },
                            onClick = { menuOpen = false; onRestoreHidden() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Power off after 1 hour") },
                        enabled = state.runtime.runtimeEnabled,
                        onClick = { menuOpen = false; onSetTimeout(1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Power off after 4 hours") },
                        enabled = state.runtime.runtimeEnabled,
                        onClick = { menuOpen = false; onSetTimeout(4) },
                    )
                    if (state.runtime.autoPowerOffAt != null) {
                        DropdownMenuItem(
                            text = { Text("Clear auto power-off") },
                            onClick = { menuOpen = false; onSetTimeout(null) },
                        )
                    }
                }
            }
        }

        if (state.runtime.lastFetchStatus == FetchStatus.Fetching) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(
                    text = "Fetching the feeds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                label = { Text("Search stories") },
                singleLine = true,
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        TextButton(onClick = { onSearch("") }) {
                            Text("Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // One scrolling row per filter axis: the wrapped chip grid pushed the news off screen.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                HeaderActionButton(
                    text = "All stories",
                    selected = state.feedViewMode == FeedViewMode.All,
                    onClick = { onSelectViewMode(FeedViewMode.All) },
                )
            }
            item {
                HeaderActionButton(
                    text = "Unread ${state.unreadCount}",
                    selected = state.feedViewMode == FeedViewMode.Unread,
                    onClick = { onSelectViewMode(FeedViewMode.Unread) },
                )
            }
            item {
                HeaderActionButton(
                    text = "Filtered ${state.keywordMatches.size}",
                    selected = state.feedViewMode == FeedViewMode.Filtered,
                    onClick = { onSelectViewMode(FeedViewMode.Filtered) },
                )
            }
            item {
                HeaderActionButton(
                    text = "Saved ${state.savedStories.size}",
                    selected = state.feedViewMode == FeedViewMode.Saved,
                    onClick = { onSelectViewMode(FeedViewMode.Saved) },
                )
            }
        }

        if (state.feedViewMode == FeedViewMode.Filtered && state.settings.keywords.isEmpty()) {
            Text(
                text = "Add keywords to build the filtered feed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                TopicChip(
                    text = "All",
                    selected = state.selectedTopic == null,
                    onClick = { onSelectTopic(null) },
                )
            }
            items(state.availableTopics, key = { it }) { topic ->
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
                color = Color(LocalAppPalette.current.accentBlue),
            )
        }

        if (!state.settings.onboardingDismissed) {
            SetupChecklistCard(
                state = state,
                onRefresh = onRefresh,
                onOpenSettings = { onOpenScreen(AppScreen.Settings) },
                onOpenFeeds = { onOpenScreen(AppScreen.Feeds) },
                onUseLocalAi = onUseLocalAi,
                onDismiss = onDismissOnboarding,
            )
        }

        state.monitors.filter { it.lastMatchStoryId != null }.forEach { monitor ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(LocalAppPalette.current.infoPanel)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Alert match ${monitor.lastMatchConfidence?.let { "${(it * 100).toInt()}%" } ?: ""}",
                        fontWeight = FontWeight.Bold,
                        color = Color(LocalAppPalette.current.accentBlue),
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
private fun AppDrawer(
    state: NewsUiState,
    current: AppScreen,
    onNavigate: (AppScreen) -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "AI News",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 4.dp),
            )
            Text(
                text = state.runtime.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, bottom = 12.dp),
            )

            DrawerSection("Reading")
            DrawerDestination("Top stories", AppScreen.Feed, current, onNavigate)
            DrawerDestination("Library", AppScreen.Library, current, onNavigate, "${state.savedStories.size}")
            DrawerDestination(
                "Filtered feed",
                AppScreen.Keywords,
                current,
                onNavigate,
                "${state.settings.keywords.size}",
            )
            DrawerDestination(
                "Hidden stories",
                AppScreen.Hidden,
                current,
                onNavigate,
                "${state.stories.count { it.isHidden }}",
            )

            DrawerSection("Feeds")
            DrawerDestination("Feeds", AppScreen.Feeds, current, onNavigate, "${state.feedSources.size}")
            DrawerDestination("Source health", AppScreen.Sources, current, onNavigate)
            DrawerDestination("Fetch history", AppScreen.History, current, onNavigate)

            DrawerSection("Alerts and automation")
            DrawerDestination("Monitors", AppScreen.Monitors, current, onNavigate, "${state.monitors.size}")
            DrawerDestination("Digests", AppScreen.Digests, current, onNavigate, "${state.digests.size}")
            DrawerDestination(
                "Schedules",
                AppScreen.Schedules,
                current,
                onNavigate,
                "${state.schedules.count { it.enabled }}",
            )

            DrawerSection("App")
            val pinContext = LocalContext.current
            NavigationDrawerItem(
                label = { Text("Add the widget to your home screen") },
                selected = false,
                onClick = { requestWidgetPin(pinContext) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            if (state.settings.aiConfigured) {
                DrawerDestination("AI usage", AppScreen.Usage, current, onNavigate)
            }
            DrawerDestination("Settings", AppScreen.Settings, current, onNavigate)
        }
    }
}

/** Asks the launcher to drop the news widget on the home screen. */
private fun requestWidgetPin(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, NewsWidgetReceiver::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(context, "Add the widget from your home screen instead", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerDestination(
    label: String,
    destination: AppScreen,
    current: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    badge: String? = null,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        badge = badge?.takeIf { it != "0" }?.let { { Text(it) } },
        selected = destination == current,
        onClick = { onNavigate(destination) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun SheetSection(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun SheetItem(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        colors = CardDefaults.cardColors(containerColor = Color(LocalAppPalette.current.infoPanel)),
        border = BorderStroke(1.dp, Color(LocalAppPalette.current.panelBorder)),
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
                        color = Color(LocalAppPalette.current.accentBlue),
                    )
                    Text(
                        text = "$completedCount/${items.size} ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(LocalAppPalette.current.accentCyan),
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
                    color = if (item.complete) {
                        Color(LocalAppPalette.current.accentCyan)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
    onToggleFetch: (String) -> Unit,
    onToggleAi: (String) -> Unit,
    onToggleNeutralTitles: (String) -> Unit,
    onSetAllFeeds: (Boolean?, Boolean?, Boolean?) -> Unit,
    onImportOpml: (String) -> Int,
    onExportOpml: () -> String,
    onRefresh: () -> Unit,
) {
    var titleDraft by remember { mutableStateOf("") }
    var urlDraft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val opml = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (opml != null) onImportOpml(opml)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(onExportOpml().toByteArray())
            }
        }
    }

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
            SettingsSection("All feeds at once") {
                Text(
                    text = "Set every feed in one go instead of editing them one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Fetching",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSetAllFeeds(true, null, null) }) {
                        Text("On for all")
                    }
                    OutlinedButton(onClick = { onSetAllFeeds(false, null, null) }) {
                        Text("Off for all")
                    }
                }
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSetAllFeeds(null, true, null) }) {
                        Text("On for all")
                    }
                    OutlinedButton(onClick = { onSetAllFeeds(null, false, null) }) {
                        Text("Off for all")
                    }
                }
                Text(
                    text = "Neutral headlines",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSetAllFeeds(null, null, true) }) {
                        Text("On for all")
                    }
                    OutlinedButton(onClick = { onSetAllFeeds(null, null, false) }) {
                        Text("Off for all")
                    }
                }
            }
        }

        item {
            SettingsSection("Add your own feed") {
                Text(
                    text = "Paste any RSS or Atom address - a newspaper, a blog, a YouTube channel - and it joins the list below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        item {
            SettingsSection("Import and export") {
                Text(
                    text = "OPML is the subscription list other readers import and export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "*/*"))
                        },
                    ) {
                        Text("Import OPML")
                    }
                    OutlinedButton(onClick = { exportLauncher.launch("ai-news-feeds.opml") }) {
                        Text("Export OPML")
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
                    ToggleRow(
                        label = "Fetch this feed",
                        checked = source.fetchEnabled,
                        onCheckedChange = { onToggleFetch(source.id) },
                    )
                    ToggleRow(
                        label = "Run AI on this feed",
                        checked = source.aiEnabled,
                        onCheckedChange = { onToggleAi(source.id) },
                    )
                    ToggleRow(
                        label = "Rewrite headlines neutrally",
                        checked = source.neutralTitlesEnabled,
                        onCheckedChange = { onToggleNeutralTitles(source.id) },
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
                            color = if (monitor.enabled) {
                                Color(LocalAppPalette.current.accentCyan)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(NewsRepository.exportBackup().toByteArray())
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (json != null) NewsRepository.importBackup(json)
    }
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
            SettingsSection("Appearance") {
                Text(
                    text = "App text size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FontScale.values().forEach { scale ->
                        TopicChip(
                            text = scale.label,
                            selected = draft.appFontScale == scale,
                            onClick = { draft = draft.copy(appFontScale = scale) },
                        )
                    }
                }

                Text(
                    text = "Widget text size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FontScale.values().forEach { scale ->
                        TopicChip(
                            text = scale.label,
                            selected = draft.widgetFontScale == scale,
                            onClick = { draft = draft.copy(widgetFontScale = scale) },
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(
                        label = "Use wallpaper colours",
                        description = "Material You picks the palette from your wallpaper",
                        checked = draft.useMaterialYou,
                        onCheckedChange = { draft = draft.copy(useMaterialYou = it) },
                    )
                }
            }
        }

        item {
            SettingsSection("Notifications") {
                ToggleRow(
                    label = "Tell me about keyword matches",
                    description = "Notify when a fetch brings in a story matching your keywords",
                    checked = draft.notifyOnKeywordMatch,
                    onCheckedChange = { draft = draft.copy(notifyOnKeywordMatch = it) },
                )
            }
        }

        item {
            SettingsSection("Backup") {
                Text(
                    text = "Saves feeds, monitors, keywords, schedules and widget presets to a file. Stories come back on the next fetch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupExportLauncher.launch("ai-news-backup.json") }) {
                        Text("Export backup")
                    }
                    OutlinedButton(
                        onClick = {
                            backupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    ) {
                        Text("Restore backup")
                    }
                }
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
                    TopicChip(
                        text = "Filtered feed",
                        selected = WIDGET_FILTERED_FEED in selectedWidgetFeedIds,
                        onClick = {
                            val nextIds = if (WIDGET_FILTERED_FEED in selectedWidgetFeedIds) {
                                emptyList()
                            } else {
                                listOf(WIDGET_FILTERED_FEED)
                            }
                            draft = draft.copy(
                                widgetFeedSourceId = nextIds.singleOrNull() ?: WIDGET_ALL_FEEDS,
                                widgetFeedSourceIds = nextIds,
                                widgetStackIndex = 0,
                            )
                        },
                    )
                    feedSources.forEach { source ->
                        TopicChip(
                            text = source.title,
                            selected = source.id in selectedWidgetFeedIds,
                            onClick = {
                                val withoutFiltered = selectedWidgetFeedIds - WIDGET_FILTERED_FEED
                                val nextIds = if (source.id in withoutFiltered) {
                                    withoutFiltered - source.id
                                } else {
                                    withoutFiltered + source.id
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
                    text = "Theme (app and widget)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppVibe.values().forEach { mode ->
                        TopicChip(
                            text = mode.label,
                            selected = draft.appVibe == mode,
                            onClick = { draft = draft.copy(appVibe = mode) },
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
                                vibe = draft.appVibe,
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
                                    appVibe = preset.vibe,
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
                text = "$feedTitle - ${preset.layoutMode.name} - ${preset.backgroundMode.name} - ${preset.vibe.name} - ${preset.densityMode.name} - ${preset.typographyMode.name}",
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
                text = "${widgetFeedTitle(preset.effectiveFeedSourceIds(), feedSources)} - ${preset.layoutMode.name} - ${preset.vibe.name} - ${preset.densityMode.name} - ${preset.typographyMode.name}",
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String = "",
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    onToggleSave: () -> Unit,
    onToggleRead: () -> Unit,
    onShare: () -> Unit,
) {
    val palette = LocalAppPalette.current
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
                    Badge("NEW", Color(palette.successPanel), Color(palette.accentCyan))
                }
                if (isAlert) {
                    Badge("ALERT", Color(palette.alertPanel), Color(palette.accentRose))
                }
                if (story.isPinned) {
                    Badge("PIN", Color(palette.goldPanel), Color(palette.accentGold))
                }
                if (story.isSaved) {
                    Badge("SAVED", Color(palette.successPanel), Color(palette.accentCyan))
                }
            }

            Text(
                text = story.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (story.isRead) FontWeight.Normal else FontWeight.Bold,
                color = if (story.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
                    Badge(it, Color(palette.infoPanel), Color(palette.accentBlue))
                }
                if (story.aiFieldsAvailable) {
                    Badge("AI", Color(palette.chipPanel), Color(palette.textMuted))
                }
            }

            // Two buttons and a menu: six side by side ran off the card.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onOpen) {
                    Text("Read story")
                }
                TextButton(onClick = onShare) {
                    Text("Share")
                }
                Spacer(Modifier.weight(1f))
                var cardMenuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { cardMenuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "Story actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = cardMenuOpen, onDismissRequest = { cardMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (story.isSaved) "Remove from library" else "Save to library") },
                            onClick = { cardMenuOpen = false; onToggleSave() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (story.isPinned) "Unpin" else "Pin to top") },
                            onClick = { cardMenuOpen = false; onTogglePin() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (story.isRead) "Mark unread" else "Mark read") },
                            onClick = { cardMenuOpen = false; onToggleRead() },
                        )
                        DropdownMenuItem(
                            text = { Text("Hide") },
                            onClick = { cardMenuOpen = false; onHide() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryDetail(
    story: NewsStory,
    section: StoryDetailSection,
    aiConfigured: Boolean,
    aiReady: Boolean,
    onBack: () -> Unit,
    onHide: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
    onRunAction: (StoryAiAction) -> Unit,
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
        if (aiConfigured) {
            item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(
                        text = "Ask AI for this story",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (aiReady) {
                            "Each button runs one request for this story only."
                        } else {
                            "Power on the runtime and AI to use these."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StoryAiAction.values().forEach { action ->
                            HeaderActionButton(
                                text = action.label,
                                enabled = aiReady,
                                onClick = { onRunAction(action) },
                            )
                        }
                    }
                }
            }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onShare) {
                    Text("Share")
                }
                OutlinedButton(onClick = onToggleSave) {
                    Text(if (story.isSaved) "Remove from library" else "Save to library")
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
            containerColor = if (highlighted) Color(LocalAppPalette.current.infoPanel) else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            if (highlighted) Color(LocalAppPalette.current.accentBlue) else MaterialTheme.colorScheme.outlineVariant,
        ),
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
                color = if (active) Color(LocalAppPalette.current.accentCyan) else Color(LocalAppPalette.current.textMuted),
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

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BackIconButton(onClick = onBack)
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
private fun LibraryScreen(
    stories: List<NewsStory>,
    onBack: () -> Unit,
    onOpenStory: (String) -> Unit,
    onToggleSave: (String) -> Unit,
    onShareStory: (NewsStory) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Library",
                subtitle = "${stories.size} saved stories",
                onBack = onBack,
            )
        }

        items(stories, key = { it.id }) { story ->
            InfoCard {
                Text(
                    text = story.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(story.displayTitle(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = story.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenStory(story.id) }) {
                        Text("Open")
                    }
                    TextButton(onClick = { onShareStory(story) }) {
                        Text("Share")
                    }
                    TextButton(onClick = { onToggleSave(story.id) }) {
                        Text("Remove")
                    }
                }
            }
        }

        if (stories.isEmpty()) {
            item {
                Text(
                    text = "Nothing saved yet. Use Save on any story to keep it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordScreen(
    keywords: List<String>,
    matches: List<NewsStory>,
    onBack: () -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onOpenStory: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Filtered feed",
                subtitle = "${keywords.size} keywords, ${matches.size} matching stories",
                onBack = onBack,
            )
        }

        item {
            SettingsSection("New keyword") {
                Text(
                    text = "Keywords are matched against titles and summaries as soon as stories arrive, without waiting for AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Keyword or phrase") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAddKeyword(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
        }

        if (keywords.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    keywords.forEach { keyword ->
                        HeaderActionButton(
                            text = "$keyword  x",
                            onClick = { onRemoveKeyword(keyword) },
                        )
                    }
                }
            }
        }

        items(matches, key = { it.id }) { story ->
            InfoCard {
                Text(
                    text = "${story.source} - ${formatStoryTime(story.publishedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(story.displayTitle(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { onOpenStory(story.id) }) {
                    Text("Open")
                }
            }
        }

        if (keywords.isNotEmpty() && matches.isEmpty()) {
            item {
                Text(
                    text = "No stories match these keywords yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history: List<FeedFetchRecord>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Fetch history",
                subtitle = "${history.size} recent feed fetches",
                onBack = onBack,
            )
        }

        if (history.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear history")
                }
            }
        }

        items(history, key = { it.id }) { record ->
            InfoCard {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(record.feedTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (record.success) "OK" else "Failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (record.success) {
                            Color(LocalAppPalette.current.accentCyan)
                        } else {
                            Color(LocalAppPalette.current.accentRose)
                        },
                    )
                }
                Text(
                    text = "${formatStoryTime(record.finishedAt)} - ${record.storyCount} stories in " +
                        "${record.finishedAt - record.startedAt} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.message.isNotBlank() && record.message != "OK") {
                    Text(
                        text = record.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "No fetches recorded yet. Refresh to fill this in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourcesScreen(
    health: List<FeedHealth>,
    onBack: () -> Unit,
    onToggleFetch: (String) -> Unit,
    onToggleAi: (String) -> Unit,
) {
    val failing = health.count { it.consecutiveFailures > 0 }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Sources",
                subtitle = if (failing == 0) {
                    "${health.size} feeds, none failing"
                } else {
                    "${health.size} feeds, $failing failing"
                },
                onBack = onBack,
            )
        }

        items(health, key = { it.feedSource.id }) { item ->
            InfoCard {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.feedSource.title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    StatusDot(active = item.healthy)
                }
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.lastSuccessAt?.let {
                    Text(
                        text = "Last good fetch ${formatStoryTime(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.lastMessage.isNotBlank() && item.lastMessage != "OK") {
                    Text(
                        text = item.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF991B1B),
                    )
                }
                ToggleRow(
                    label = "Fetch this feed",
                    checked = item.feedSource.fetchEnabled,
                    onCheckedChange = { onToggleFetch(item.feedSource.id) },
                )
                ToggleRow(
                    label = "Run AI on this feed",
                    checked = item.feedSource.aiEnabled,
                    onCheckedChange = { onToggleAi(item.feedSource.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsageScreen(
    state: NewsUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onToggleAi: () -> Unit,
    onRegenerate: () -> Unit,
    onRegenerateKind: (StoryAiAction) -> Unit,
) {
    val totalCents = state.usageRecords.sumOf { it.costCents }
    val totalTokens = state.usageRecords.sumOf { it.tokens }
    val byAction = state.usageRecords.groupBy { it.action }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "AI usage",
                subtitle = if (totalTokens > 0) {
                    "${state.usageRecords.size} requests, $totalTokens tokens, ${totalCents}c"
                } else {
                    "${state.usageRecords.size} requests, ${totalCents}c estimated"
                },
                onBack = onBack,
            )
        }

        item {
            InfoCard {
                Text("Queue", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${state.runtime.aiQueueStatus} - ${state.pendingAiCount} stories still waiting for AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${state.runtime.aiBudgetText} of ${state.settings.aiDailyBudgetCents}c daily budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRegenerate,
                        enabled = state.runtime.runtimeEnabled && state.runtime.aiEnabled,
                    ) {
                        Text("Fill missing AI")
                    }
                    OutlinedButton(onClick = onToggleAi) {
                        Text(if (state.runtime.aiEnabled) "Pause AI" else "Resume AI")
                    }
                }
                Text(
                    text = "Fill one kind at a time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StoryAiAction.values().forEach { action ->
                        HeaderActionButton(
                            text = action.label,
                            enabled = state.runtime.runtimeEnabled && state.runtime.aiEnabled,
                            onClick = { onRegenerateKind(action) },
                        )
                    }
                }
            }
        }

        if (byAction.isNotEmpty()) {
            item {
                InfoCard {
                    Text("Totals by action", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    byAction.forEach { (action, records) ->
                        Text(
                            text = "$action - ${records.size} requests, " +
                                "${records.sumOf { it.tokens }} tokens, ${records.sumOf { it.costCents }}c",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onReset) {
                        Text("Reset usage")
                    }
                }
            }
        }

        items(state.usageRecords, key = { it.id }) { record ->
            InfoCard {
                Text(
                    text = "${record.action} - ${record.provider}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = record.storyTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (record.tokens > 0) {
                        "${formatStoryTime(record.createdAt)} - ${record.tokens} tokens - ${record.costCents}c"
                    } else {
                        "${formatStoryTime(record.createdAt)} - ${record.costCents}c"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.usageRecords.isEmpty()) {
            item {
                Text(
                    text = "No AI requests recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DigestScreen(
    digests: List<DigestEntry>,
    onBack: () -> Unit,
    onBuildDigest: () -> Unit,
    onDeleteDigest: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Digests",
                subtitle = "${digests.size} saved digests",
                onBack = onBack,
            )
        }

        item {
            SettingsSection("Build a digest") {
                Text(
                    text = "A digest is a short list of the current top stories. It is also posted as a notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBuildDigest) {
                    Text("Build now")
                }
            }
        }

        items(digests, key = { it.id }) { digest ->
            InfoCard {
                Text(digest.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = formatStoryTime(digest.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = digest.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onDeleteDigest(digest.id) }) {
                    Text("Delete")
                }
            }
        }

        if (digests.isEmpty()) {
            item {
                Text(
                    text = "No digests yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleScreen(
    schedules: List<NewsSchedule>,
    onBack: () -> Unit,
    onAddSchedule: (ScheduleKind, Int) -> Unit,
    onToggleSchedule: (String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onRunSchedule: (String) -> Unit,
) {
    var selectedKind by remember { mutableStateOf(ScheduleKind.Refresh) }
    var hourDraft by remember { mutableStateOf("8") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        item {
            ScreenHeader(
                title = "Schedules",
                subtitle = "${schedules.count { it.enabled }} of ${schedules.size} running daily",
                onBack = onBack,
            )
        }

        item {
            SettingsSection("New schedule") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScheduleKind.values().forEach { kind ->
                        HeaderActionButton(
                            text = kind.label,
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                        )
                    }
                }
                OutlinedTextField(
                    value = hourDraft,
                    onValueChange = { hourDraft = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hour of day (0-23)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAddSchedule(selectedKind, hourDraft.toIntOrNull()?.coerceIn(0, 23) ?: 8)
                    },
                    enabled = hourDraft.isNotBlank(),
                ) {
                    Text("Add schedule")
                }
            }
        }

        items(schedules, key = { it.id }) { schedule ->
            InfoCard {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${schedule.kind.label} at ${schedule.hour}:00",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = schedule.lastRunAt
                                ?.let { "Last run ${formatStoryTime(it)}" }
                                ?: "Not run yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { onToggleSchedule(schedule.id) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onRunSchedule(schedule.id) }) {
                        Text("Run now")
                    }
                    TextButton(onClick = { onDeleteSchedule(schedule.id) }) {
                        Text("Delete")
                    }
                }
            }
        }

        if (schedules.isEmpty()) {
            item {
                Text(
                    text = "No schedules yet. Add one to refresh, scan or digest at a set hour.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
