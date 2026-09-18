package com.ainews.android.data

import android.content.Context
import androidx.room.Room
import com.ainews.android.network.RssFeedFetcher
import com.ainews.android.worker.RefreshNewsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

object NewsRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var storyDao: StoryDao
    private lateinit var runtimePreferences: RuntimePreferences
    private val rssFeedFetcher = RssFeedFetcher()

    private fun seedStories(now: Long = System.currentTimeMillis()) = listOf(
        NewsStory(
            id = "bg-health-1",
            source = "Bulgaria Health Brief",
            sourceUrl = "https://example.com/health",
            publishedAt = now - 35 * 60 * 1000,
            fetchedAt = now - 10 * 60 * 1000,
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
            publishedAt = now - 70 * 60 * 1000,
            fetchedAt = now - 10 * 60 * 1000,
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
            publishedAt = now - 2 * 60 * 60 * 1000,
            fetchedAt = now - 10 * 60 * 1000,
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

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        val database = Room.databaseBuilder(
            appContext,
            AiNewsDatabase::class.java,
            "ai-news.db",
        ).build()

        storyDao = database.storyDao()
        runtimePreferences = RuntimePreferences(appContext)

        repositoryScope.launch {
            if (storyDao.countStories() == 0) {
                storyDao.upsertStories(seedStories().map { it.toEntity() })
            }
        }

        repositoryScope.launch {
            combine(
                storyDao.observeStories(),
                runtimePreferences.runtimeState,
            ) { stories, runtime ->
                stories.map { it.toModel() } to runtime
            }.collect { (stories, runtime) ->
                _state.update { current ->
                    current.copy(stories = stories, runtime = runtime)
                }
            }
        }
    }

    fun selectStory(storyId: String?) {
        _state.update { it.copy(selectedStoryId = storyId, message = null) }
    }

    fun toggleRuntime() {
        _state.update { current ->
            val enabled = !current.runtime.runtimeEnabled
            val runtime = current.runtime.copy(
                runtimeEnabled = enabled,
                lastFetchStatus = if (enabled) FetchStatus.Idle else FetchStatus.Paused,
            )
            persistRuntime(runtime)
            if (enabled) {
                RefreshNewsWorker.schedule(appContext)
            } else {
                RefreshNewsWorker.cancel(appContext)
            }
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
            val runtime = current.runtime.copy(
                aiEnabled = enabled,
                aiQueueStatus = if (enabled) "Idle" else "Paused",
            )
            persistRuntime(runtime)
            current.copy(
                runtime = runtime,
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
            val runtime = current.runtime.copy(
                lastFetchStartedAt = System.currentTimeMillis(),
                lastFetchStatus = FetchStatus.Fetching,
            )
            persistRuntime(runtime)
            current.copy(
                runtime = runtime,
                message = "Fetching latest stories",
            )
        }

        val previousStories = _state.value.stories
        val previousIds = previousStories.map { it.id }.toSet()
        val hiddenById = previousStories.associateBy({ it.id }, { it.isHidden to it.hiddenAt })

        val fetchedStories = withContext(Dispatchers.IO) {
            runCatching { rssFeedFetcher.fetchTopStories() }.getOrDefault(emptyList())
        }
        delay(200)

        if (fetchedStories.isEmpty()) {
            _state.update { current ->
                val runtime = current.runtime.copy(
                    lastFetchFinishedAt = System.currentTimeMillis(),
                    lastFetchStatus = FetchStatus.Failed,
                )
                persistRuntime(runtime)
                current.copy(
                    runtime = runtime,
                    message = "Refresh failed - keeping saved stories",
                )
            }
            return
        }

        val fetchedIds = fetchedStories.map { it.id }.toSet()
        val sameStorySet = fetchedIds == previousIds
        val storiesToStore = fetchedStories.map { story ->
            val hiddenState = hiddenById[story.id]
            story.copy(
                isNew = !sameStorySet && story.id !in previousIds,
                isHidden = hiddenState?.first ?: false,
                hiddenAt = hiddenState?.second,
            )
        }

        _state.update { current ->
            val runtime = current.runtime.copy(
                lastFetchFinishedAt = System.currentTimeMillis(),
                lastFetchStatus = FetchStatus.Success,
            )
            persistRuntime(runtime)
            current.copy(
                runtime = runtime,
                stories = storiesToStore,
                message = if (sameStorySet) {
                    "Feed refreshed - no new stories"
                } else {
                    "Feed refreshed - ${storiesToStore.count { it.isNew }} new"
                },
            )
        }
        withContext(Dispatchers.IO) {
            storyDao.upsertStories(storiesToStore.map { it.toEntity() })
            if (sameStorySet) {
                storyDao.clearNewMarkers()
            }
        }
    }

    fun hideStory(storyId: String) {
        _state.update { current ->
            val nextSelected = current.selectedStoryId.takeUnless { it == storyId }
            repositoryScope.launch {
                storyDao.hideStory(storyId, System.currentTimeMillis())
            }
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
        repositoryScope.launch {
            storyDao.restoreHidden()
        }
        _state.update { current ->
            current.copy(
                stories = current.stories.map { it.copy(isHidden = false, hiddenAt = null) },
                message = "Hidden stories restored",
            )
        }
    }

    private fun persistRuntime(runtime: RuntimeState) {
        repositoryScope.launch {
            runtimePreferences.save(runtime)
        }
    }
}
