package com.ainews.android.data

enum class FetchStatus {
    Idle,
    Fetching,
    Paused,
    Failed,
    Success,
}

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
            return "$runtime - $fetch"
        }
}

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
    val lastMatchExplanation: String? = null,
)

data class NewsUiState(
    val runtime: RuntimeState = RuntimeState(),
    val feedTitle: String = "Top Stories",
    val stories: List<NewsStory> = emptyList(),
    val selectedStoryId: String? = null,
    val monitors: List<NewsMonitor> = emptyList(),
    val message: String? = null,
) {
    val visibleStories: List<NewsStory>
        get() = stories.filterNot { it.isHidden }

    val selectedStory: NewsStory?
        get() = stories.firstOrNull { it.id == selectedStoryId }
}
