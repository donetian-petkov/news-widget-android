package com.ainews.android.data

import android.content.Context
import androidx.room.Room
import com.ainews.android.network.RemoteBackendClient
import com.ainews.android.network.AiEnrichmentClient
import com.ainews.android.network.ImageDiskCache
import com.ainews.android.network.RssFeedFetcher
import com.ainews.android.worker.AutoPowerOffWorker
import com.ainews.android.worker.MonitorScanWorker
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
import java.time.LocalDate

object NewsRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var storyDao: StoryDao
    private lateinit var runtimePreferences: RuntimePreferences
    private lateinit var secureProviderKeyStore: SecureProviderKeyStore
    private val rssFeedFetcher = RssFeedFetcher()
    private val remoteBackendClient = RemoteBackendClient()
    private val aiEnrichmentClient = AiEnrichmentClient()
    private val imageDiskCache = ImageDiskCache()

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
            monitors = defaultNewsMonitors,
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
        secureProviderKeyStore = SecureProviderKeyStore(appContext)

        repositoryScope.launch {
            if (storyDao.countStories() == 0) {
                storyDao.upsertStories(seedStories().map { it.toEntity() })
            }
        }

        repositoryScope.launch {
            combine(
                storyDao.observeStories(),
                runtimePreferences.runtimeState,
                runtimePreferences.monitorState,
                runtimePreferences.feedSourceState,
            ) { stories, runtimeWithSettings, monitors, feedSources ->
                CombinedState(
                    stories.map { it.toModel() },
                    runtimeWithSettings.first to runtimeWithSettings.second,
                    monitors,
                    feedSources,
                )
            }.collect { combined ->
                val stories = combined.stories
                val runtimeWithSettings = combined.runtimeWithSettings
                val monitors = combined.monitors
                val (runtime, settings) = runtimeWithSettings
                _state.update { current ->
                    current.copy(
                        stories = stories,
                        runtime = runtime,
                        settings = settings.copy(
                            providerKeySaved = settings.providerKeySaved || secureProviderKeyStore.hasKey(),
                        ),
                        monitors = monitors,
                        feedSources = combined.feedSources,
                    )
                }
            }
        }
    }

    private data class CombinedState(
        val stories: List<NewsStory>,
        val runtimeWithSettings: Pair<RuntimeState, RuntimeSettings>,
        val monitors: List<NewsMonitor>,
        val feedSources: List<FeedSource>,
    )

    fun selectStory(storyId: String?) {
        _state.update { it.copy(selectedStoryId = storyId, message = null) }
    }

    fun selectTopic(topic: String?) {
        _state.update {
            it.copy(
                selectedTopic = topic,
                message = topic?.let { selected -> "Filtering $selected" } ?: "Showing all topics",
            )
        }
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
                RefreshNewsWorker.schedule(appContext, current.settings.fetchCadenceMinutes)
            } else {
                RefreshNewsWorker.cancel(appContext)
                AutoPowerOffWorker.cancel(appContext)
            }
            sendRemoteRuntimeIfNeeded(current.settings, enabled)
            current.copy(
                runtime = runtime,
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

    fun updateSettings(settings: RuntimeSettings) {
        val normalized = settings.copy(
            fetchCadenceMinutes = settings.fetchCadenceMinutes.coerceAtLeast(15),
            monitorScanHour = settings.monitorScanHour.coerceIn(0, 23),
            aiDailyBudgetCents = settings.aiDailyBudgetCents.coerceAtLeast(0),
            providerKeySaved = secureProviderKeyStore.hasKey(),
            onboardingDismissed = settings.onboardingDismissed,
        )
        _state.update { current ->
            persistSettings(normalized)
            if (current.runtime.runtimeEnabled) {
                RefreshNewsWorker.schedule(appContext, normalized.fetchCadenceMinutes)
            }
            MonitorScanWorker.schedule(appContext, normalized.monitorScanHour)
            current.copy(
                settings = normalized,
                message = "Settings saved",
            )
        }
    }

    fun saveProviderKey(key: String) {
        secureProviderKeyStore.save(key)
        val nextSettings = _state.value.settings.copy(providerKeySaved = secureProviderKeyStore.hasKey())
        updateSettings(nextSettings)
    }

    fun clearProviderKey() {
        secureProviderKeyStore.clear()
        val nextSettings = _state.value.settings.copy(providerKeySaved = false)
        persistSettings(nextSettings)
        _state.update {
            it.copy(settings = nextSettings, message = "Provider key cleared")
        }
    }

    fun dismissOnboarding() {
        val nextSettings = _state.value.settings.copy(onboardingDismissed = true)
        persistSettings(nextSettings)
        _state.update {
            it.copy(settings = nextSettings, message = "Setup checklist hidden")
        }
    }

    fun addMonitor(sentence: String) {
        val cleanSentence = sentence.trim()
        if (cleanSentence.isBlank()) return
        val monitor = NewsMonitor(
            id = "monitor-${System.currentTimeMillis()}",
            sentence = cleanSentence,
        )
        _state.update { current ->
            val monitors = current.monitors + monitor
            persistMonitors(monitors)
            current.copy(monitors = monitors, message = "Monitor added")
        }
    }

    fun toggleMonitor(monitorId: String) {
        _state.update { current ->
            val monitors = current.monitors.map { monitor ->
                if (monitor.id == monitorId) {
                    monitor.copy(enabled = !monitor.enabled)
                } else {
                    monitor
                }
            }
            persistMonitors(monitors)
            current.copy(monitors = monitors, message = "Monitor updated")
        }
    }

    fun deleteMonitor(monitorId: String) {
        _state.update { current ->
            val monitors = current.monitors.filterNot { it.id == monitorId }
            persistMonitors(monitors)
            current.copy(
                monitors = monitors,
                alertMatches = current.alertMatches.filterNot { it.monitorId == monitorId },
                message = "Monitor deleted",
            )
        }
    }

    fun addFeedSource(title: String, url: String) {
        val cleanTitle = title.trim()
        val cleanUrl = url.trim()
        if (cleanTitle.isBlank() || cleanUrl.isBlank()) return
        val feedSource = FeedSource(
            id = "feed-${System.currentTimeMillis()}",
            title = cleanTitle,
            url = cleanUrl,
        )
        _state.update { current ->
            val feedSources = current.feedSources + feedSource
            persistFeedSources(feedSources)
            current.copy(feedSources = feedSources, message = "Feed added")
        }
    }

    fun deleteFeedSource(feedSourceId: String) {
        _state.update { current ->
            val feedSources = current.feedSources.filterNot { it.id == feedSourceId }
                .ifEmpty { defaultFeedSources }
            persistFeedSources(feedSources)
            current.copy(feedSources = feedSources, message = "Feed removed")
        }
    }

    fun resetFeedSources() {
        persistFeedSources(defaultFeedSources)
        _state.update {
            it.copy(feedSources = defaultFeedSources, message = "Default feeds restored")
        }
    }

    suspend fun enrichVisibleStories(limit: Int = 3) {
        val current = _state.value
        if (!current.runtime.runtimeEnabled || !current.runtime.aiEnabled) {
            _state.update {
                it.copy(
                    runtime = it.runtime.copy(aiQueueStatus = "Paused"),
                    message = "Power on runtime and AI before enrichment",
                )
            }
            return
        }

        val candidates = current.prioritizedStories
            .filterNot { it.aiFieldsAvailable }
            .take(limit)

        if (candidates.isEmpty()) {
            _state.update { it.copy(message = "No stories need enrichment") }
            return
        }

        val settings = _state.value.settings
        val apiKey = secureProviderKeyStore.load()
        val today = LocalDate.now().toString()
        val runtimeWithBudgetDay = current.runtime.resetAiBudgetIfNeeded(today)
        val paidEnrichment = settings.aiProvider == AiProvider.OpenAI && !apiKey.isNullOrBlank()
        val estimatedCostCents = if (paidEnrichment) candidates.size * OPENAI_ENRICHMENT_ESTIMATE_CENTS else 0
        if (paidEnrichment && runtimeWithBudgetDay.aiBudgetSpentCents + estimatedCostCents > settings.aiDailyBudgetCents) {
            val runtime = runtimeWithBudgetDay.copy(aiQueueStatus = "Budget paused")
            persistRuntime(runtime)
            _state.update {
                it.copy(
                    runtime = runtime,
                    message = "AI budget reached - enrichment paused",
                )
            }
            return
        }

        _state.update { state ->
            val runtime = runtimeWithBudgetDay.copy(aiQueueStatus = "Running")
            persistRuntime(runtime)
            state.copy(runtime = runtime, message = "Enriching ${candidates.size} stories")
        }

        var enrichedCount = 0

        withContext(Dispatchers.IO) {
            candidates.forEach { story ->
                val enrichment = runCatching {
                    when {
                        settings.aiProvider == AiProvider.OpenAI && !apiKey.isNullOrBlank() ->
                            aiEnrichmentClient.enrichWithOpenAi(apiKey, story)

                        else -> aiEnrichmentClient.enrichLocally(story)
                    }
                }.getOrElse {
                    aiEnrichmentClient.enrichLocally(story)
                }

                storyDao.updateEnrichment(
                    storyId = story.id,
                    neutralTitle = enrichment.neutralTitle,
                    translation = enrichment.translation,
                    research = enrichment.research,
                    topicLabels = enrichment.topicLabels.joinToString("|"),
                )
                enrichedCount += 1
            }
        }

        _state.update { state ->
            val runtime = state.runtime.copy(
                lastAiJobAt = System.currentTimeMillis(),
                aiQueueStatus = "Idle",
                aiBudgetSpentCents = if (paidEnrichment) {
                    runtimeWithBudgetDay.aiBudgetSpentCents + estimatedCostCents
                } else {
                    runtimeWithBudgetDay.aiBudgetSpentCents
                },
                aiBudgetDay = today,
            )
            persistRuntime(runtime)
            state.copy(
                runtime = runtime,
                message = "Enriched $enrichedCount stories",
            )
        }
    }

    fun setAutoPowerOff(hoursFromNow: Long?) {
        val autoPowerOffAt = hoursFromNow?.let {
            System.currentTimeMillis() + it * 60 * 60 * 1000
        }
        _state.update { current ->
            val runtime = current.runtime.copy(autoPowerOffAt = autoPowerOffAt)
            persistRuntime(runtime)
            if (autoPowerOffAt == null) {
                AutoPowerOffWorker.cancel(appContext)
            } else {
                AutoPowerOffWorker.schedule(appContext, autoPowerOffAt)
            }
            current.copy(
                runtime = runtime,
                message = if (hoursFromNow == null) {
                    "Auto power-off cleared"
                } else {
                    "Auto power-off set for ${hoursFromNow}h"
                },
            )
        }
    }

    fun powerOffFromTimeout() {
        _state.update { current ->
            val runtime = current.runtime.copy(
                runtimeEnabled = false,
                aiEnabled = false,
                autoPowerOffAt = null,
                lastFetchStatus = FetchStatus.Paused,
                aiQueueStatus = "Paused",
            )
            persistRuntime(runtime)
            RefreshNewsWorker.cancel(appContext)
            current.copy(
                runtime = runtime,
                message = "Auto power-off completed",
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
            if (current.settings.backendMode == BackendMode.RemoteBackend) {
                sendRemoteRefreshIfNeeded(current.settings)
            }
            current.copy(
                runtime = runtime,
                message = "Fetching latest stories",
            )
        }

        val previousStories = _state.value.stories
        val previousIds = previousStories.map { it.id }.toSet()
        val hiddenById = previousStories.associateBy({ it.id }, { it.isHidden to it.hiddenAt })

        val settings = _state.value.settings
        val fetchedStories = withContext(Dispatchers.IO) {
            runCatching {
                when (settings.backendMode) {
                    BackendMode.NativeRuntime -> rssFeedFetcher.fetchTopStories(sources = _state.value.feedSources)
                    BackendMode.RemoteBackend -> remoteBackendClient.fetchStories(settings.remoteBackendUrl)
                }
            }.getOrDefault(emptyList())
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
            imageDiskCache.prefetch(appContext, storiesToStore.mapNotNull { it.imageUrl })
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
            sendRemoteHideIfNeeded(current.settings, storyId)
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

    fun restoreStory(storyId: String) {
        repositoryScope.launch {
            storyDao.restoreStory(storyId)
        }
        _state.update { current ->
            current.copy(
                stories = current.stories.map { story ->
                    if (story.id == storyId) {
                        story.copy(isHidden = false, hiddenAt = null)
                    } else {
                        story
                    }
                },
                message = "Story restored",
            )
        }
    }

    fun scanMonitorsNow() {
        val current = _state.value
        val matches = current.monitors
            .filter { it.enabled }
            .mapNotNull { monitor ->
                current.visibleStories
                    .asSequence()
                    .map { story -> story to MonitorMatcher.evaluate(monitor, story) }
                    .filter { (_, decision) -> decision.matched }
                    .maxByOrNull { (_, decision) -> decision.confidence }
                    ?.let { (story, decision) ->
                        AlertMatch(
                            id = "${monitor.id}-${story.id}",
                            monitorId = monitor.id,
                            storyId = story.id,
                            confidence = decision.confidence,
                            explanation = decision.explanation,
                            matchedAt = System.currentTimeMillis(),
                        )
                    }
            }

        _state.update { state ->
            val monitors = state.monitors.map { monitor ->
                val match = matches.firstOrNull { it.monitorId == monitor.id }
                if (match == null) {
                    monitor.copy(
                        lastMatchStoryId = null,
                        lastMatchConfidence = null,
                        lastMatchExplanation = null,
                    )
                } else {
                    monitor.copy(
                        lastMatchStoryId = match.storyId,
                        lastMatchConfidence = match.confidence,
                        lastMatchExplanation = match.explanation,
                    )
                }
            }
            persistMonitors(monitors)
            state.copy(
                monitors = monitors,
                alertMatches = matches,
                message = if (matches.isEmpty()) {
                    "Monitor scan complete - no matches"
                } else {
                    "Monitor scan found ${matches.size} match"
                },
            )
        }
    }

    private fun persistRuntime(runtime: RuntimeState) {
        repositoryScope.launch {
            runtimePreferences.save(runtime)
        }
    }

    private fun persistSettings(settings: RuntimeSettings) {
        repositoryScope.launch {
            runtimePreferences.saveSettings(settings)
        }
    }

    private fun persistMonitors(monitors: List<NewsMonitor>) {
        repositoryScope.launch {
            runtimePreferences.saveMonitors(monitors)
        }
    }

    private fun persistFeedSources(feedSources: List<FeedSource>) {
        repositoryScope.launch {
            runtimePreferences.saveFeedSources(feedSources)
        }
    }

    private fun sendRemoteRuntimeIfNeeded(settings: RuntimeSettings, enabled: Boolean) {
        if (settings.backendMode != BackendMode.RemoteBackend) return
        repositoryScope.launch {
            runCatching { remoteBackendClient.setRuntime(settings.remoteBackendUrl, enabled) }
        }
    }

    private fun sendRemoteRefreshIfNeeded(settings: RuntimeSettings) {
        if (settings.backendMode != BackendMode.RemoteBackend) return
        repositoryScope.launch {
            runCatching { remoteBackendClient.requestRefresh(settings.remoteBackendUrl) }
        }
    }

    private fun sendRemoteHideIfNeeded(settings: RuntimeSettings, storyId: String) {
        if (settings.backendMode != BackendMode.RemoteBackend) return
        repositoryScope.launch {
            runCatching { remoteBackendClient.hideStory(settings.remoteBackendUrl, storyId) }
        }
    }

    private fun RuntimeState.resetAiBudgetIfNeeded(today: String): RuntimeState =
        if (aiBudgetDay == today) {
            this
        } else {
            copy(aiBudgetSpentCents = 0, aiBudgetDay = today)
        }

    private const val OPENAI_ENRICHMENT_ESTIMATE_CENTS = 2
}
