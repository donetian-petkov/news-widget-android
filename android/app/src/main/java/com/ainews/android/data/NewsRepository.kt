package com.ainews.android.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object NewsRepository {
    private val seedStories = listOf(
        NewsStory(
            id = "bg-health-1",
            source = "Bulgaria Health Brief",
            sourceUrl = "https://example.com/health",
            publishedAt = System.currentTimeMillis() - 35 * 60 * 1000,
            fetchedAt = System.currentTimeMillis() - 10 * 60 * 1000,
            title = "Public flu vaccination calendar expected next week",
            summary = "Health officials said the national schedule should clarify pharmacy availability, priority groups, and GP distribution windows.",
            topicLabels = listOf("Public health", "Bulgaria"),
            aiFieldsAvailable = true,
            isNew = true,
            neutralTitle = "Authorities may publish the flu vaccination calendar next week",
            translation = "The story concerns vaccine availability timing in Bulgaria.",
            research = "Potential monitor match: public vaccination availability.",
        ),
        NewsStory(
            id = "energy-grid-1",
            source = "Grid Watch",
            sourceUrl = "https://example.com/grid",
            publishedAt = System.currentTimeMillis() - 70 * 60 * 1000,
            fetchedAt = System.currentTimeMillis() - 10 * 60 * 1000,
            title = "Regional grid upgrades move into winter readiness phase",
            summary = "Operators are prioritizing substations near major demand corridors as cold-weather load forecasts rise.",
            topicLabels = listOf("Energy", "Infrastructure"),
            aiFieldsAvailable = true,
            isNew = true,
            neutralTitle = "Grid operators prepare winter upgrades",
            translation = "No translation is needed for the current app language.",
            research = "Useful for energy infrastructure monitoring.",
        ),
        NewsStory(
            id = "ai-policy-1",
            source = "Policy Ledger",
            sourceUrl = "https://example.com/ai-policy",
            publishedAt = System.currentTimeMillis() - 2 * 60 * 60 * 1000,
            fetchedAt = System.currentTimeMillis() - 10 * 60 * 1000,
            title = "EU agencies publish implementation notes for AI procurement",
            summary = "The guidance focuses on risk documentation, supplier evaluation, and record keeping for public-sector AI tools.",
            topicLabels = listOf("AI policy", "EU"),
            aiFieldsAvailable = true,
            isNew = false,
            neutralTitle = "EU agencies clarify AI procurement expectations",
            translation = "The story describes public procurement guidance.",
            research = "Relevant for AI governance and public-sector compliance.",
        ),
    )

    private val _state = MutableStateFlow(
        NewsUiState(
            stories = seedStories,
            monitors = listOf(
                NewsMonitor(
                    id = "flu-bg",
                    sentence = "when flu vaccinations will be available to the public in Bulgaria",
                    lastMatchStoryId = "bg-health-1",
                    lastMatchExplanation = "The top health story mentions expected public vaccination calendar availability.",
                ),
            ),
        ),
    )

    val state: StateFlow<NewsUiState> = _state

    fun selectStory(storyId: String?) {
        _state.update { it.copy(selectedStoryId = storyId, message = null) }
    }

    fun toggleRuntime() {
        _state.update { current ->
            val enabled = !current.runtime.runtimeEnabled
            current.copy(
                runtime = current.runtime.copy(
                    runtimeEnabled = enabled,
                    lastFetchStatus = if (enabled) FetchStatus.Idle else FetchStatus.Paused,
                ),
                message = if (enabled) "Runtime on" else "Runtime off",
            )
        }
    }

    fun toggleAi() {
        _state.update { current ->
            val enabled = !current.runtime.aiEnabled
            current.copy(
                runtime = current.runtime.copy(
                    aiEnabled = enabled,
                    aiQueueStatus = if (enabled) "Idle" else "Paused",
                ),
                message = if (enabled) "AI enrichment on" else "AI enrichment off",
            )
        }
    }

    suspend fun refreshNow() {
        if (!_state.value.runtime.runtimeEnabled || !_state.value.runtime.fetchEnabled) {
            _state.update {
                it.copy(
                    runtime = it.runtime.copy(lastFetchStatus = FetchStatus.Paused),
                    message = "Power on runtime before refreshing",
                )
            }
            return
        }

        _state.update { current ->
            current.copy(
                runtime = current.runtime.copy(
                    lastFetchStartedAt = System.currentTimeMillis(),
                    lastFetchStatus = FetchStatus.Fetching,
                ),
                message = "Fetching latest stories",
            )
        }

        delay(650)

        _state.update { current ->
            current.copy(
                runtime = current.runtime.copy(
                    lastFetchFinishedAt = System.currentTimeMillis(),
                    lastFetchStatus = FetchStatus.Success,
                ),
                stories = current.stories.map { it.copy(isNew = false) },
                message = "Feed refreshed - no new stories",
            )
        }
    }

    fun hideStory(storyId: String) {
        _state.update { current ->
            val nextSelected = current.selectedStoryId.takeUnless { it == storyId }
            current.copy(
                selectedStoryId = nextSelected,
                stories = current.stories.map { story ->
                    if (story.id == storyId) {
                        story.copy(isHidden = true, hiddenAt = System.currentTimeMillis())
                    } else {
                        story
                    }
                },
                message = "Story hidden",
            )
        }
    }

    fun restoreHidden() {
        _state.update { current ->
            current.copy(
                stories = current.stories.map { it.copy(isHidden = false, hiddenAt = null) },
                message = "Hidden stories restored",
            )
        }
    }
}
