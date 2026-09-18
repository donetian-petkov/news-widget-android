package com.ainews.android.data

enum class FetchStatus {
    Idle,
    Fetching,
    Paused,
    Failed,
    Success,
}

enum class BackendMode {
    NativeRuntime,
    RemoteBackend,
}

enum class AiProvider {
    OpenAI,
    Anthropic,
    LocalOnly,
}

enum class WidgetLayoutMode {
    Column,
    Stack,
}

enum class WidgetBackgroundMode {
    Solid,
    Transparent,
}

enum class WidgetThemeMode {
    Light,
    Dark,
}

enum class WidgetDensityMode {
    Comfortable,
    Compact,
}

data class WidgetPreset(
    val id: String,
    val name: String,
    val feedSourceId: String = WIDGET_ALL_FEEDS,
    val feedSourceIds: List<String> = emptyList(),
    val layoutMode: WidgetLayoutMode = WidgetLayoutMode.Column,
    val backgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Solid,
    val themeMode: WidgetThemeMode = WidgetThemeMode.Light,
    val densityMode: WidgetDensityMode = WidgetDensityMode.Comfortable,
)

data class RuntimeSettings(
    val backendMode: BackendMode = BackendMode.NativeRuntime,
    val aiProvider: AiProvider = AiProvider.OpenAI,
    val remoteBackendUrl: String = "",
    val fetchCadenceMinutes: Long = 30,
    val monitorScanHour: Int = 20,
    val aiDailyBudgetCents: Int = 100,
    val providerKeySaved: Boolean = false,
    val onboardingDismissed: Boolean = false,
    val widgetFeedSourceId: String = WIDGET_ALL_FEEDS,
    val widgetFeedSourceIds: List<String> = emptyList(),
    val widgetLayoutMode: WidgetLayoutMode = WidgetLayoutMode.Column,
    val widgetBackgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Solid,
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.Light,
    val widgetDensityMode: WidgetDensityMode = WidgetDensityMode.Comfortable,
    val widgetStackIndex: Int = 0,
    val widgetPresets: List<WidgetPreset> = emptyList(),
)

data class RuntimeState(
    val runtimeEnabled: Boolean = true,
    val fetchEnabled: Boolean = true,
    val aiEnabled: Boolean = true,
    val autoPowerOffAt: Long? = null,
    val lastFetchStartedAt: Long? = null,
    val lastFetchFinishedAt: Long? = null,
    val lastFetchStatus: FetchStatus = FetchStatus.Idle,
    val lastAiJobAt: Long? = null,
    val aiQueueStatus: String = "Idle",
    val aiBudgetSpentCents: Int = 0,
    val aiBudgetDay: String = "",
) {
    val statusText: String
        get() {
            val runtime = if (runtimeEnabled) "Runtime on" else "Runtime off"
            val fetch = when {
                !fetchEnabled -> "Fetch off"
                lastFetchStatus == FetchStatus.Fetching -> "Fetching"
                !runtimeEnabled -> "Fetch paused"
                lastFetchStatus == FetchStatus.Failed -> "Fetch failed"
                else -> "Fetch ready"
            }
            val timeout = if (runtimeEnabled && autoPowerOffAt != null) " - Auto off armed" else ""
            return "$runtime - $fetch$timeout"
        }
}

val RuntimeState.aiBudgetText: String
    get() = "AI spent ${aiBudgetSpentCents}c today"

data class NewsStory(
    val id: String,
    val source: String,
    val sourceUrl: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val title: String,
    val summary: String,
    val imageUrl: String? = null,
    val topicLabels: List<String>,
    val aiFieldsAvailable: Boolean,
    val isNew: Boolean,
    val isHidden: Boolean = false,
    val hiddenAt: Long? = null,
    val neutralTitle: String? = null,
    val translation: String? = null,
    val research: String? = null,
)

data class NewsMonitor(
    val id: String,
    val sentence: String,
    val enabled: Boolean = true,
    val lastMatchStoryId: String? = null,
    val lastMatchConfidence: Double? = null,
    val lastMatchExplanation: String? = null,
)

val defaultNewsMonitors = listOf(
    NewsMonitor(
        id = "flu-bg",
        sentence = "when flu vaccinations will be available to the public in Bulgaria",
    ),
)

data class AlertMatch(
    val id: String,
    val monitorId: String,
    val storyId: String,
    val confidence: Double,
    val explanation: String,
    val matchedAt: Long,
)

data class NewsUiState(
    val runtime: RuntimeState = RuntimeState(),
    val feedTitle: String = "Top Stories",
    val stories: List<NewsStory> = emptyList(),
    val selectedStoryId: String? = null,
    val selectedTopic: String? = null,
    val monitors: List<NewsMonitor> = emptyList(),
    val alertMatches: List<AlertMatch> = emptyList(),
    val settings: RuntimeSettings = RuntimeSettings(),
    val feedSources: List<FeedSource> = defaultFeedSources,
    val message: String? = null,
) {
    val visibleStories: List<NewsStory>
        get() = stories
            .filterNot { it.isHidden }
            .filter { story -> selectedTopic == null || selectedTopic in story.topicLabels }

    val availableTopics: List<String>
        get() = stories
            .filterNot { it.isHidden }
            .flatMap { it.topicLabels }
            .distinct()
            .sorted()

    val prioritizedStories: List<NewsStory>
        get() {
            val alertStoryIds = alertMatches.map { it.storyId }.toSet()
            return visibleStories.sortedWith(
                compareByDescending<NewsStory> { it.id in alertStoryIds }
                    .thenByDescending { it.publishedAt },
            )
        }

    val selectedStory: NewsStory?
        get() = stories.firstOrNull { it.id == selectedStoryId }

    val widgetFeedTitle: String
        get() {
            val selectedFeeds = widgetSelectedFeedSources
            return when (selectedFeeds.size) {
                0 -> "All Feeds"
                1 -> selectedFeeds.single().title
                else -> "${selectedFeeds.size} Feeds"
            }
        }

    val widgetSelectedFeedSources: List<FeedSource>
        get() {
            val selectedIds = settings.effectiveWidgetFeedSourceIds().toSet()
            return feedSources.filter { it.id in selectedIds }
        }

    val widgetStories: List<NewsStory>
        get() {
            val selectedFeedTitles = widgetSelectedFeedSources.map { it.title }.toSet()
            return prioritizedStories.filter { story ->
                selectedFeedTitles.isEmpty() || story.source in selectedFeedTitles
            }
        }

    val widgetStackIndex: Int
        get() {
            val storyCount = widgetStories.size
            if (storyCount == 0) return 0
            return ((settings.widgetStackIndex % storyCount) + storyCount) % storyCount
        }
}

const val WIDGET_ALL_FEEDS = "all"

fun RuntimeSettings.effectiveWidgetFeedSourceIds(): List<String> =
    widgetFeedSourceIds.ifEmpty {
        listOf(widgetFeedSourceId).filterNot { it == WIDGET_ALL_FEEDS }
    }.distinct()

fun WidgetPreset.effectiveFeedSourceIds(): List<String> =
    feedSourceIds.ifEmpty {
        listOf(feedSourceId).filterNot { it == WIDGET_ALL_FEEDS }
    }.distinct()
