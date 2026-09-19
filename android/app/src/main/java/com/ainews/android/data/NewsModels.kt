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

enum class WidgetDensityMode {
    Comfortable,
    Compact,
}

enum class WidgetTypographyMode {
    Standard,
    Large,
}

enum class StoryDetailSection {
    Story,
    Summary,
    Research,
    Translation,
}

enum class StoryAiAction {
    Summary,
    Research,
    Translation,
    NeutralTitle,
    ;

    val label: String
        get() = when (this) {
            Summary -> "Summary"
            Research -> "Research"
            Translation -> "Translation"
            NeutralTitle -> "Neutral title"
        }
}

enum class ScheduleKind {
    Refresh,
    MonitorScan,
    Digest,
    ;

    val label: String
        get() = when (this) {
            Refresh -> "Refresh feeds"
            MonitorScan -> "Scan monitors"
            Digest -> "Build digest"
        }
}

enum class FeedViewMode {
    All,
    Unread,
    Filtered,
    Saved,
}

enum class FontScale {
    Small,
    Medium,
    Large,
    ExtraLarge,
    ;

    val label: String
        get() = when (this) {
            Small -> "Small"
            Medium -> "Medium"
            Large -> "Large"
            ExtraLarge -> "Extra large"
        }

    val scale: Float
        get() = when (this) {
            Small -> 0.85f
            Medium -> 1.0f
            Large -> 1.25f
            ExtraLarge -> 1.5f
        }
}

data class NewsSchedule(
    val id: String,
    val kind: ScheduleKind,
    val hour: Int,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
)

data class DigestEntry(
    val id: String,
    val createdAt: Long,
    val title: String,
    val body: String,
    val storyIds: List<String> = emptyList(),
)

data class AiUsageRecord(
    val id: String,
    val createdAt: Long,
    val action: String,
    val provider: String,
    val storyTitle: String,
    val costCents: Int,
    val tokens: Int = 0,
)

data class FeedFetchRecord(
    val id: String,
    val feedId: String,
    val feedTitle: String,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val storyCount: Int,
    val message: String,
)

data class FeedHealth(
    val feedSource: FeedSource,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastStoryCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastMessage: String = "",
) {
    val healthy: Boolean
        get() = consecutiveFailures == 0 && lastSuccessAt != null

    val statusText: String
        get() = when {
            lastAttemptAt == null -> "Never fetched"
            consecutiveFailures > 0 -> "Failing ($consecutiveFailures in a row)"
            else -> "$lastStoryCount stories last fetch"
        }
}

data class WidgetPreset(
    val id: String,
    val name: String,
    val feedSourceId: String = WIDGET_ALL_FEEDS,
    val feedSourceIds: List<String> = emptyList(),
    val layoutMode: WidgetLayoutMode = WidgetLayoutMode.Column,
    val backgroundMode: WidgetBackgroundMode = WidgetBackgroundMode.Solid,
    val vibe: AppVibe = AppVibe.System,
    val densityMode: WidgetDensityMode = WidgetDensityMode.Comfortable,
    val typographyMode: WidgetTypographyMode = WidgetTypographyMode.Standard,
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
    val appVibe: AppVibe = AppVibe.System,
    val widgetDensityMode: WidgetDensityMode = WidgetDensityMode.Comfortable,
    val widgetTypographyMode: WidgetTypographyMode = WidgetTypographyMode.Standard,
    val widgetStackIndex: Int = 0,
    val widgetPresets: List<WidgetPreset> = emptyList(),
    val keywords: List<String> = emptyList(),
    val appFontScale: FontScale = FontScale.Medium,
    val widgetFontScale: FontScale = FontScale.Medium,
    val useMaterialYou: Boolean = false,
    val notifyOnKeywordMatch: Boolean = true,
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

/** AI is only usable once a provider key is stored, or the user deliberately picked local mode. */
val RuntimeSettings.aiConfigured: Boolean
    get() = providerKeySaved || aiProvider == AiProvider.LocalOnly

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
    val isPinned: Boolean = false,
    val pinnedAt: Long? = null,
    val isSaved: Boolean = false,
    val savedAt: Long? = null,
    val isRead: Boolean = false,
    val readAt: Long? = null,
    val neutralTitle: String? = null,
    val translation: String? = null,
    val research: String? = null,
)

val defaultNewsTopics = listOf(
    "AI",
    "AI policy",
    "Bulgaria",
    "EU",
    "Energy",
    "Infrastructure",
    "Policy",
    "Public health",
    "Security",
    "World",
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
    val selectedStorySection: StoryDetailSection = StoryDetailSection.Story,
    val selectedTopic: String? = null,
    val monitors: List<NewsMonitor> = emptyList(),
    val alertMatches: List<AlertMatch> = emptyList(),
    val settings: RuntimeSettings = RuntimeSettings(),
    val feedSources: List<FeedSource> = defaultFeedSources,
    val feedViewMode: FeedViewMode = FeedViewMode.All,
    val searchQuery: String = "",
    val fetchHistory: List<FeedFetchRecord> = emptyList(),
    val digests: List<DigestEntry> = emptyList(),
    val usageRecords: List<AiUsageRecord> = emptyList(),
    val schedules: List<NewsSchedule> = emptyList(),
    val message: String? = null,
) {
    val visibleStories: List<NewsStory>
        get() = stories
            .filterNot { it.isHidden }
            .filter { story -> selectedTopic == null || selectedTopic in story.topicLabels }
            .filter { story ->
                when (feedViewMode) {
                    FeedViewMode.All -> true
                    FeedViewMode.Unread -> !story.isRead
                    FeedViewMode.Filtered -> KeywordMatcher.matches(settings.keywords, story)
                    FeedViewMode.Saved -> story.isSaved
                }
            }
            .filter { story -> story.matchesSearch(searchQuery) }

    val savedStories: List<NewsStory>
        get() = stories.filter { it.isSaved }.sortedByDescending { it.savedAt ?: it.publishedAt }

    val keywordMatches: List<NewsStory>
        get() = stories
            .filterNot { it.isHidden }
            .filter { KeywordMatcher.matches(settings.keywords, it) }
            .sortedByDescending { it.publishedAt }

    val unreadCount: Int
        get() = stories.count { !it.isHidden && !it.isRead }

    val pendingAiCount: Int
        get() = stories.count { !it.isHidden && !it.aiFieldsAvailable }

    val feedHealth: List<FeedHealth>
        get() = feedSources.map { source ->
            val records = fetchHistory.filter { it.feedId == source.id }.sortedByDescending { it.finishedAt }
            val latest = records.firstOrNull()
            FeedHealth(
                feedSource = source,
                lastAttemptAt = latest?.finishedAt,
                lastSuccessAt = records.firstOrNull { it.success }?.finishedAt,
                lastStoryCount = latest?.storyCount ?: 0,
                consecutiveFailures = records.takeWhile { !it.success }.size,
                lastMessage = latest?.message.orEmpty(),
            )
        }

    val availableTopics: List<String>
        get() = (
            defaultNewsTopics + stories
                .filterNot { it.isHidden }
                .flatMap { it.topicLabels }
            )
            .distinct()
            .sorted()

    val prioritizedStories: List<NewsStory>
        get() {
            val alertStoryIds = alertMatches.map { it.storyId }.toSet()
            return visibleStories.sortedWith(
                compareByDescending<NewsStory> { it.isPinned }
                    .thenByDescending { it.id in alertStoryIds }
                    .thenByDescending { it.publishedAt },
            )
        }

    val selectedStory: NewsStory?
        get() = stories.firstOrNull { it.id == selectedStoryId }

    val widgetShowsFilteredFeed: Boolean
        get() = WIDGET_FILTERED_FEED in settings.effectiveWidgetFeedSourceIds()

    val widgetFeedTitle: String
        get() {
            if (widgetShowsFilteredFeed) return "Filtered Feed"
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
            if (widgetShowsFilteredFeed) {
                return prioritizedStories.filter { KeywordMatcher.matches(settings.keywords, it) }
            }
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

/** Pseudo feed id: the widget shows keyword matches instead of one RSS source. */
const val WIDGET_FILTERED_FEED = "filtered"

fun RuntimeSettings.effectiveWidgetFeedSourceIds(): List<String> =
    widgetFeedSourceIds.ifEmpty {
        listOf(widgetFeedSourceId).filterNot { it == WIDGET_ALL_FEEDS }
    }.distinct()

fun WidgetPreset.effectiveFeedSourceIds(): List<String> =
    feedSourceIds.ifEmpty {
        listOf(feedSourceId).filterNot { it == WIDGET_ALL_FEEDS }
    }.distinct()

fun NewsStory.matchesSearch(query: String): Boolean {
    val clean = query.trim()
    if (clean.isBlank()) return true
    val haystack = listOfNotNull(title, summary, neutralTitle, research, translation, source)
        .joinToString(" ")
        .lowercase(java.util.Locale.getDefault())
    return clean.lowercase(java.util.Locale.getDefault())
        .split(" ")
        .filter { it.isNotBlank() }
        .all { haystack.contains(it) }
}
