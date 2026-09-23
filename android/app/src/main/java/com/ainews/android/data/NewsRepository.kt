package com.ainews.android.data

import android.content.Context
import androidx.room.Room
import com.ainews.android.network.RemoteBackendClient
import com.ainews.android.network.AiEnrichmentClient
import com.ainews.android.network.ImageDiskCache
import com.ainews.android.network.FeedFetchOutcome
import com.ainews.android.network.RssFeedFetcher
import androidx.glance.appwidget.updateAll
import com.ainews.android.notifications.AlertNotifier
import com.ainews.android.widget.NewsWidget
import com.ainews.android.worker.AiAutoOffWorker
import com.ainews.android.worker.AutoPowerOffWorker
import com.ainews.android.worker.MonitorScanWorker
import com.ainews.android.worker.RefreshAlarmReceiver
import com.ainews.android.worker.RefreshNewsWorker
import com.ainews.android.worker.ScheduleWorker
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object NewsRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var storyDao: StoryDao
    private lateinit var fetchHistoryDao: FetchHistoryDao
    private lateinit var runtimePreferences: RuntimePreferences
    private lateinit var secureProviderKeyStore: SecureProviderKeyStore
    private val refreshLock = java.util.concurrent.Semaphore(1)
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
        )
            .addMigrations(
                AiNewsDatabase.MIGRATION_1_2,
                AiNewsDatabase.MIGRATION_2_3,
                AiNewsDatabase.MIGRATION_3_4,
            )
            .build()

        storyDao = database.storyDao()
        fetchHistoryDao = database.fetchHistoryDao()
        runtimePreferences = RuntimePreferences(appContext)
        secureProviderKeyStore = SecureProviderKeyStore(appContext)

        // AI may have been left on from a previous run; make sure it is counting down.
        repositoryScope.launch {
            kotlinx.coroutines.delay(2_000)
            armAiSession()
        }

        repositoryScope.launch {
            if (storyDao.countStories() == 0) {
                storyDao.upsertStories(seedStories().map { it.toEntity() })
            }
            // A fresh install should not sit on the seeded stories until the first
            // scheduled run comes around.
            if (storyDao.countStories() <= seedStories().size) {
                runCatching { refreshNow() }
            }
        }

        repositoryScope.launch {
            val storiesWithHistory = combine(
                storyDao.observeStories(),
                fetchHistoryDao.observeHistory(),
            ) { stories, history ->
                stories.map { it.toModel() } to history.map { it.toModel() }
            }

            var syncedSchedules: List<NewsSchedule>? = null

            combine(
                storiesWithHistory,
                runtimePreferences.runtimeState,
                runtimePreferences.monitorState,
                runtimePreferences.feedSourceState,
                runtimePreferences.extrasState,
            ) { storiesAndHistory, runtimeWithSettings, monitors, feedSources, extras ->
                CombinedState(
                    stories = storiesAndHistory.first,
                    fetchHistory = storiesAndHistory.second,
                    runtimeWithSettings = runtimeWithSettings.first to runtimeWithSettings.second,
                    monitors = monitors,
                    feedSources = feedSources,
                    extras = extras,
                )
            }.collect { combined ->
                val (runtime, settings) = combined.runtimeWithSettings
                _state.update { current ->
                    current.copy(
                        stories = combined.stories,
                        fetchHistory = combined.fetchHistory,
                        runtime = runtime,
                        settings = settings.copy(
                            providerKeySaved = settings.providerKeySaved || secureProviderKeyStore.hasKey(),
                        ),
                        monitors = combined.monitors,
                        feedSources = combined.feedSources,
                        schedules = combined.extras.schedules,
                        digests = combined.extras.digests,
                        usageRecords = combined.extras.usageRecords,
                    )
                }
                if (combined.extras.schedules != syncedSchedules) {
                    syncedSchedules = combined.extras.schedules
                    ScheduleWorker.syncAll(appContext, combined.extras.schedules)
                }
            }
        }
    }

    private data class CombinedState(
        val stories: List<NewsStory>,
        val fetchHistory: List<FeedFetchRecord>,
        val runtimeWithSettings: Pair<RuntimeState, RuntimeSettings>,
        val monitors: List<NewsMonitor>,
        val feedSources: List<FeedSource>,
        val extras: PreferenceExtras,
    )

    fun selectStory(storyId: String?, section: StoryDetailSection = StoryDetailSection.Story) {
        storyId?.let { markRead(it) }
        _state.update { it.copy(selectedStoryId = storyId, selectedStorySection = section, message = null) }
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
                scheduleRefreshes(current.settings.fetchCadenceMinutes)
            } else {
                RefreshNewsWorker.cancel(appContext)
                RefreshAlarmReceiver.cancel(appContext)
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
            val stopAt = if (enabled) System.currentTimeMillis() + AI_SESSION_MILLIS else null
            val runtime = current.runtime.copy(
                aiEnabled = enabled,
                aiEnabledUntil = stopAt,
                aiQueueStatus = if (enabled) "Idle" else "Paused",
            )
            persistRuntime(runtime)
            if (stopAt != null) {
                AiAutoOffWorker.schedule(appContext, stopAt)
            } else {
                AiAutoOffWorker.cancel(appContext)
            }
            current.copy(
                runtime = runtime,
                message = if (enabled) {
                    "AI enrichment on, off again in ${AI_SESSION_MILLIS / 3_600_000} hours"
                } else {
                    "AI enrichment off"
                },
            )
        }
    }

    /** Called by the timer: AI has been on long enough, stop it. */
    fun stopAiFromTimeout() {
        _state.update { current ->
            if (!current.runtime.aiEnabled) return@update current
            val runtime = current.runtime.copy(
                aiEnabled = false,
                aiEnabledUntil = null,
                aiQueueStatus = "Paused",
            )
            persistRuntime(runtime)
            current.copy(runtime = runtime, message = "AI stopped after its session ran out")
        }
    }

    /** Arms the timer when AI is already on from a previous run and nothing is counting down. */
    private fun armAiSession() {
        _state.update { current ->
            if (!current.runtime.aiEnabled) return@update current
            val existing = current.runtime.aiEnabledUntil
            if (existing != null && existing > System.currentTimeMillis()) {
                AiAutoOffWorker.schedule(appContext, existing)
                return@update current
            }
            val stopAt = System.currentTimeMillis() + AI_SESSION_MILLIS
            val runtime = current.runtime.copy(aiEnabledUntil = stopAt)
            persistRuntime(runtime)
            AiAutoOffWorker.schedule(appContext, stopAt)
            current.copy(runtime = runtime)
        }
    }

    fun updateSettings(settings: RuntimeSettings) {
        val feedSources = _state.value.feedSources
        val validFeedIds = feedSources.map { it.id }.toSet()
        val normalizedWidgetFeedIds = settings.effectiveWidgetFeedSourceIds()
            .filter { it in validFeedIds || it == WIDGET_FILTERED_FEED }
        val normalized = settings.copy(
            fetchCadenceMinutes = settings.fetchCadenceMinutes.coerceIn(5, 24 * 60),
            monitorScanHour = settings.monitorScanHour.coerceIn(0, 23),
            aiDailyBudgetCents = settings.aiDailyBudgetCents.coerceAtLeast(0),
            providerKeySaved = secureProviderKeyStore.hasKey(),
            onboardingDismissed = settings.onboardingDismissed,
            widgetFeedSourceId = normalizedWidgetFeedIds.singleOrNull() ?: WIDGET_ALL_FEEDS,
            widgetFeedSourceIds = normalizedWidgetFeedIds,
            widgetLayoutMode = settings.widgetLayoutMode,
            widgetBackgroundMode = settings.widgetBackgroundMode,
            appVibe = settings.appVibe,
            widgetDensityMode = settings.widgetDensityMode,
            widgetTypographyMode = settings.widgetTypographyMode,
            widgetStackIndex = settings.widgetStackIndex.coerceAtLeast(0),
            keywords = KeywordMatcher.normalize(settings.keywords),
            widgetPresets = settings.widgetPresets
                .filter { it.name.isNotBlank() }
                .distinctBy { it.id }
                .take(12)
                .map { preset ->
                    preset.copy(
                        name = preset.name.trim(),
                        feedSourceId = preset.effectiveFeedSourceIds()
                            .filter { it in validFeedIds || it == WIDGET_FILTERED_FEED }
                            .singleOrNull()
                            ?: WIDGET_ALL_FEEDS,
                        feedSourceIds = preset.effectiveFeedSourceIds()
                            .filter { it in validFeedIds || it == WIDGET_FILTERED_FEED },
                    )
                },
        )
        _state.update { current ->
            persistSettings(normalized)
            if (current.runtime.runtimeEnabled) {
                scheduleRefreshes(normalized.fetchCadenceMinutes)
            }
            MonitorScanWorker.schedule(appContext, normalized.monitorScanHour)
            current.copy(
                settings = normalized,
                message = "Settings saved",
            )
        }
    }

    fun moveWidgetStack(offset: Int) {
        _state.update { current ->
            val storyCount = current.widgetStories.size
            if (storyCount == 0) return@update current
            val nextIndex = ((current.widgetStackIndex + offset) % storyCount + storyCount) % storyCount
            val nextSettings = current.settings.copy(widgetStackIndex = nextIndex)
            persistSettings(nextSettings)
            current.copy(
                settings = nextSettings,
                message = "Widget stack ${nextIndex + 1}/$storyCount",
            )
        }
    }

    fun saveProviderKey(key: String) {
        secureProviderKeyStore.save(key)
        val saved = secureProviderKeyStore.hasKey()
        val nextSettings = _state.value.settings.copy(providerKeySaved = saved)
        updateSettings(nextSettings)
        // Saving used to happen in silence, so there was no way to tell it had worked.
        _state.update {
            it.copy(message = if (saved) "Provider key saved" else "That key could not be saved")
        }
    }

    /**
     * Sends one real request to the provider so the reader can see whether the key works,
     * rather than finding out hours later that no summary ever appeared.
     */
    suspend fun testProviderKey(): String = withContext(Dispatchers.IO) {
        val key = secureProviderKeyStore.load()
        if (key.isNullOrBlank()) return@withContext "No key saved yet"
        val story = _state.value.visibleStories.firstOrNull()
            ?: return@withContext "No story to try it on yet"
        val result = runCatching {
            aiEnrichmentClient.runActionWithOpenAi(key, story, StoryAiAction.Summary)
        }
        android.util.Log.i("AiNewsKeyTest", "asked the provider, success=${result.isSuccess}")
        val message = result.fold(
            onSuccess = { text ->
                if (text.isBlank()) "The provider answered, but with nothing in it" else "Key works"
            },
            onFailure = { failure -> "Key did not work: ${failure.message ?: failure}" },
        )
        message
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

    fun useLocalAiProvider() {
        updateSettings(_state.value.settings.copy(aiProvider = AiProvider.LocalOnly))
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

    /** Applies one switch to every feed, so the list does not have to be edited one by one. */
    fun setAllFeeds(fetchEnabled: Boolean? = null, aiEnabled: Boolean? = null, neutralTitles: Boolean? = null) {
        _state.update { current ->
            val feedSources = current.feedSources.map { source ->
                source.copy(
                    fetchEnabled = fetchEnabled ?: source.fetchEnabled,
                    aiEnabled = aiEnabled ?: source.aiEnabled,
                    neutralTitlesEnabled = neutralTitles ?: source.neutralTitlesEnabled,
                )
            }
            persistFeedSources(feedSources)
            val message = when {
                fetchEnabled != null -> if (fetchEnabled) "Fetching every feed" else "Fetching paused for every feed"
                aiEnabled != null -> if (aiEnabled) "AI on for every feed" else "AI paused for every feed"
                neutralTitles != null -> if (neutralTitles) "Neutral titles on everywhere" else "Neutral titles off everywhere"
                else -> null
            }
            current.copy(feedSources = feedSources, message = message)
        }
    }

    fun toggleFeedFetch(feedSourceId: String) {
        _state.update { current ->
            val feedSources = current.feedSources.map { source ->
                if (source.id == feedSourceId) source.copy(fetchEnabled = !source.fetchEnabled) else source
            }
            persistFeedSources(feedSources)
            val changed = feedSources.firstOrNull { it.id == feedSourceId }
            current.copy(
                feedSources = feedSources,
                message = changed?.let {
                    if (it.fetchEnabled) "${it.title} fetching on" else "${it.title} fetching off"
                },
            )
        }
    }

    fun toggleFeedAi(feedSourceId: String) {
        _state.update { current ->
            val feedSources = current.feedSources.map { source ->
                if (source.id == feedSourceId) source.copy(aiEnabled = !source.aiEnabled) else source
            }
            persistFeedSources(feedSources)
            val changed = feedSources.firstOrNull { it.id == feedSourceId }
            current.copy(
                feedSources = feedSources,
                message = changed?.let {
                    if (it.aiEnabled) "${it.title} AI on" else "${it.title} AI paused"
                },
            )
        }
    }

    fun importOpml(opml: String): Int {
        val imported = OpmlCodec.parse(opml)
        if (imported.isEmpty()) {
            _state.update { it.copy(message = "No feeds found in that OPML file") }
            return 0
        }
        var addedCount = 0
        _state.update { current ->
            val existingUrls = current.feedSources.map { it.url.trim().lowercase() }.toSet()
            val additions = imported.filterNot { it.url.trim().lowercase() in existingUrls }
            addedCount = additions.size
            if (additions.isEmpty()) {
                return@update current.copy(message = "Those feeds are already in the list")
            }
            val feedSources = current.feedSources + additions
            persistFeedSources(feedSources)
            current.copy(feedSources = feedSources, message = "Imported ${additions.size} feeds")
        }
        return addedCount
    }

    fun exportOpml(): String = OpmlCodec.write(_state.value.feedSources)

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
            .filter { aiEnabledForStory(current.feedSources, it) }
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
                recordUsage(
                    action = "Enrichment",
                    provider = if (paidEnrichment) "openai" else "local",
                    storyTitle = story.title,
                    costCents = if (paidEnrichment) OPENAI_ENRICHMENT_ESTIMATE_CENTS else 0,
                    tokens = if (paidEnrichment) aiEnrichmentClient.consumeTokenUsage().total else 0,
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
            RefreshAlarmReceiver.cancel(appContext)
            current.copy(
                runtime = runtime,
                message = "Auto power-off completed",
            )
        }
    }

    /**
     * Only one fetch runs at a time. Two overlapping fetches used to leave the status stuck
     * on "Updating" forever, because the slower one wrote its start state after the faster
     * one had already finished.
     */
    /**
     * @param manual the reader pressed refresh themselves, which also means they are looking
     * at the widget right now: whatever is already on it stops being new.
     */
    suspend fun refreshNow(manual: Boolean = false) {
        if (!refreshLock.tryAcquire()) return
        try {
            refreshNowLocked(manual)
        } finally {
            refreshLock.release()
            // Whatever happened, the widget must not be left claiming it is still fetching.
            if (_state.value.runtime.lastFetchStatus == FetchStatus.Fetching) {
                _state.update { current ->
                    val runtime = current.runtime.copy(
                        lastFetchStatus = FetchStatus.Failed,
                        lastFetchFinishedAt = System.currentTimeMillis(),
                    )
                    persistRuntime(runtime)
                    current.copy(runtime = runtime)
                }
            }
        }
    }

    private suspend fun refreshNowLocked(manual: Boolean = false) {
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

        val settings = _state.value.settings
        val fetchOutcome = withContext(Dispatchers.IO) {
            runCatching {
                when (settings.backendMode) {
                    BackendMode.NativeRuntime ->
                        rssFeedFetcher.fetchTopStoriesWithHistory(sources = _state.value.feedSources)

                    BackendMode.RemoteBackend -> FeedFetchOutcome(
                        stories = remoteBackendClient.fetchStories(settings.remoteBackendUrl),
                        records = emptyList(),
                    )
                }
            }
                .onFailure { android.util.Log.e("AiNewsRefresh", "fetch failed", it) }
                .getOrDefault(FeedFetchOutcome(emptyList(), emptyList()))
        }
        val fetchedStories = fetchOutcome.stories
        if (fetchOutcome.records.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                fetchHistoryDao.insertAll(fetchOutcome.records.map { it.toEntity() })
                fetchHistoryDao.prune(System.currentTimeMillis() - HISTORY_RETENTION_MILLIS)
            }
        }
        delay(200)

        if (fetchedStories.isEmpty()) {
            // A fetch attempted while the phone is dozing or offline fails every feed at
            // once with a DNS error. That is not the feeds being broken, so do not say so.
            val offline = !hasNetwork(appContext)
            _state.update { current ->
                val runtime = current.runtime.copy(
                    lastFetchFinishedAt = System.currentTimeMillis(),
                    lastFetchStatus = if (offline) FetchStatus.Idle else FetchStatus.Failed,
                )
                persistRuntime(runtime)
                current.copy(
                    runtime = runtime,
                    message = if (offline) {
                        "No connection - keeping saved stories"
                    } else {
                        "Refresh failed - keeping saved stories"
                    },
                )
            }
            return
        }

        android.util.Log.i("AiNewsRefresh", "fetched ${fetchedStories.size} stories")
        val refreshResult = StoryRefreshMerger.merge(
            previousStories = _state.value.stories,
            fetchedStories = fetchedStories,
            clearExistingNew = manual,
        )
        val storiesToStore = refreshResult.stories
        val previousIds = _state.value.stories.map { it.id }.toSet()
        val keywordArrivals = storiesToStore
            .filter { it.id !in previousIds }
            .filter { KeywordMatcher.matches(settings.keywords, it) }

        _state.update { current ->
            val runtime = current.runtime.copy(
                lastFetchFinishedAt = System.currentTimeMillis(),
                lastFetchStatus = FetchStatus.Success,
            )
            persistRuntime(runtime)
            current.copy(
                runtime = runtime,
                stories = storiesToStore,
                message = refreshResult.message,
            )
        }
        if (settings.notifyOnKeywordMatch && keywordArrivals.isNotEmpty()) {
            AlertNotifier(appContext).notifyKeywordMatches(keywordArrivals, settings.keywords)
        }
        withContext(Dispatchers.IO) {
            storyDao.upsertStories(storiesToStore.map { it.toEntity() })
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

    fun togglePinned(storyId: String) {
        _state.update { current ->
            val story = current.stories.firstOrNull { it.id == storyId } ?: return@update current
            val nextPinned = !story.isPinned
            val pinnedAt = if (nextPinned) System.currentTimeMillis() else null
            repositoryScope.launch {
                storyDao.setPinned(storyId, nextPinned, pinnedAt)
            }
            current.copy(
                stories = current.stories.map {
                    if (it.id == storyId) {
                        it.copy(isPinned = nextPinned, pinnedAt = pinnedAt)
                    } else {
                        it
                    }
                },
                message = if (nextPinned) "Story pinned" else "Story unpinned",
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun markRead(storyId: String, read: Boolean = true) {
        val readAt = if (read) System.currentTimeMillis() else null
        repositoryScope.launch {
            storyDao.setRead(storyId, read, readAt)
            // Having read it is what ends its life as a new story.
            if (read) storyDao.clearNewMarker(storyId)
        }
        _state.update { current ->
            current.copy(
                stories = current.stories.map {
                    when {
                        it.id != storyId -> it
                        read -> it.copy(isRead = true, readAt = readAt, isNew = false)
                        else -> it.copy(isRead = false, readAt = null)
                    }
                },
            )
        }
    }

    /**
     * Ends the NEW badge on everything that was already on the widget when the reader last
     * arrived at their home screen. Stories that landed after that keep theirs.
     */
    suspend fun clearNewMarkersSeenBefore(seenAt: Long) {
        withContext(Dispatchers.IO) { storyDao.clearNewMarkersSeenBefore(seenAt) }
        _state.update { current ->
            current.copy(
                stories = current.stories.map {
                    if (it.fetchedAt <= seenAt) it.copy(isNew = false) else it
                },
            )
        }
    }

    fun markAllRead() {
        val readAt = System.currentTimeMillis()
        repositoryScope.launch {
            storyDao.markAllRead(readAt)
            // Reading everything also clears the NEW flags, otherwise the badges stay up and
            // the button looks like it did nothing.
            storyDao.clearNewMarkers()
        }
        _state.update { current ->
            current.copy(
                stories = current.stories.map {
                    when {
                        it.isHidden -> it
                        it.isRead -> it.copy(isNew = false)
                        else -> it.copy(isRead = true, readAt = readAt, isNew = false)
                    }
                },
                message = "All stories marked read",
            )
        }
    }

    /** Fills in one AI field for every story still missing it. */
    suspend fun regenerateMissing(action: StoryAiAction, limit: Int = 10) {
        val current = _state.value
        val candidates = current.prioritizedStories
            .filter { aiEnabledForStory(current.feedSources, it) }
            .filter { story ->
                when (action) {
                    // A feed nearly always ships its own description, so "missing" here
                    // means the story has not been through AI yet, not that it has no text.
                    StoryAiAction.Summary -> !story.aiFieldsAvailable
                    StoryAiAction.Research -> story.research.isNullOrBlank()
                    StoryAiAction.Translation -> story.translation.isNullOrBlank()
                    StoryAiAction.NeutralTitle ->
                        story.neutralTitle.isNullOrBlank() && neutralTitlesEnabledForStory(current.feedSources, story)
                }
            }
            .take(limit)

        if (candidates.isEmpty()) {
            _state.update { it.copy(message = "Nothing is missing ${action.label.lowercase()}") }
            return
        }
        _state.update { it.copy(message = "Running ${action.label.lowercase()} for ${candidates.size} stories") }
        candidates.forEach { story -> runStoryAction(story.id, action) }
        _state.update { it.copy(message = "${action.label} filled for ${candidates.size} stories") }
    }

    /** Writes everything the app knows into one JSON document the user can keep. */
    fun exportBackup(): String {
        val current = _state.value
        return JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("feedSources", JSONArray(RuntimePreferencesCodec.feedSourcesToJson(current.feedSources)))
            .put("monitors", JSONArray(RuntimePreferencesCodec.monitorsToJson(current.monitors)))
            .put("keywords", JSONArray(current.settings.keywords))
            .put("schedules", JSONArray(RuntimePreferencesCodec.schedulesToJson(current.schedules)))
            .put("widgetPresets", JSONArray(RuntimePreferencesCodec.widgetPresetsToJson(current.settings.widgetPresets)))
            .put("savedStoryIds", JSONArray(current.savedStories.map { it.id }))
            .toString(2)
    }

    /** Restores a backup produced by [exportBackup]. Stories themselves come back on the next fetch. */
    fun importBackup(json: String): Boolean {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: run {
            _state.update { it.copy(message = "That file is not an AI News backup") }
            return false
        }
        val feedSources = parsed.optJSONArray("feedSources")
            ?.let { RuntimePreferencesCodec.feedSourcesFromJson(it.toString()) }
            .orEmpty()
        val monitors = parsed.optJSONArray("monitors")
            ?.let { RuntimePreferencesCodec.monitorsFromJson(it.toString()) }
            .orEmpty()
        val schedules = parsed.optJSONArray("schedules")
            ?.let { RuntimePreferencesCodec.schedulesFromJson(it.toString()) }
            .orEmpty()
        val presets = parsed.optJSONArray("widgetPresets")
            ?.let { RuntimePreferencesCodec.widgetPresetsFromJson(it.toString()) }
            .orEmpty()
        val keywords = parsed.optJSONArray("keywords")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty()

        if (feedSources.isNotEmpty()) persistFeedSources(feedSources)
        if (monitors.isNotEmpty()) persistMonitors(monitors)
        if (schedules.isNotEmpty()) persistSchedules(schedules)

        _state.update { current ->
            val settings = current.settings.copy(
                keywords = keywords.ifEmpty { current.settings.keywords },
                widgetPresets = presets.ifEmpty { current.settings.widgetPresets },
            )
            persistSettings(settings)
            current.copy(
                settings = settings,
                feedSources = feedSources.ifEmpty { current.feedSources },
                monitors = monitors.ifEmpty { current.monitors },
                schedules = schedules.ifEmpty { current.schedules },
                message = "Backup restored",
            )
        }
        return true
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun neutralTitlesEnabledForStory(feedSources: List<FeedSource>, story: NewsStory): Boolean =
        feedSources.firstOrNull { it.title == story.source }?.neutralTitlesEnabled ?: true

    fun toggleFeedNeutralTitles(feedSourceId: String) {
        _state.update { current ->
            val feedSources = current.feedSources.map { source ->
                if (source.id == feedSourceId) {
                    source.copy(neutralTitlesEnabled = !source.neutralTitlesEnabled)
                } else {
                    source
                }
            }
            persistFeedSources(feedSources)
            val changed = feedSources.firstOrNull { it.id == feedSourceId }
            current.copy(
                feedSources = feedSources,
                message = changed?.let {
                    if (it.neutralTitlesEnabled) "${it.title} neutral titles on" else "${it.title} neutral titles off"
                },
            )
        }
    }

    fun setFeedViewMode(mode: FeedViewMode) {
        _state.update { current ->
            current.copy(
                feedViewMode = mode,
                message = when (mode) {
                    FeedViewMode.All -> "Showing all stories"
                    FeedViewMode.Unread -> "Showing unread stories"
                    FeedViewMode.Filtered -> "Showing keyword matches"
                    FeedViewMode.Saved -> "Showing saved stories"
                },
            )
        }
    }

    fun toggleSaved(storyId: String) {
        _state.update { current ->
            val story = current.stories.firstOrNull { it.id == storyId } ?: return@update current
            val nextSaved = !story.isSaved
            val savedAt = if (nextSaved) System.currentTimeMillis() else null
            repositoryScope.launch {
                storyDao.setSaved(storyId, nextSaved, savedAt)
            }
            current.copy(
                stories = current.stories.map {
                    if (it.id == storyId) it.copy(isSaved = nextSaved, savedAt = savedAt) else it
                },
                message = if (nextSaved) "Saved to library" else "Removed from library",
            )
        }
    }

    fun addKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isBlank()) return
        val settings = _state.value.settings
        if (settings.keywords.any { it.equals(clean, ignoreCase = true) }) return
        updateSettings(settings.copy(keywords = settings.keywords + clean))
    }

    fun removeKeyword(keyword: String) {
        val settings = _state.value.settings
        updateSettings(settings.copy(keywords = settings.keywords.filterNot { it.equals(keyword, ignoreCase = true) }))
    }

    /** Runs a single AI action for one story, so the user can ask for just what they need. */
    suspend fun runStoryAction(storyId: String, action: StoryAiAction) {
        val current = _state.value
        val story = current.stories.firstOrNull { it.id == storyId } ?: return
        if (!current.runtime.runtimeEnabled || !current.runtime.aiEnabled) {
            _state.update { it.copy(message = "Power on runtime and AI before running ${action.label.lowercase()}") }
            return
        }
        if (!aiEnabledForStory(current.feedSources, story)) {
            _state.update { it.copy(message = "AI is paused for ${story.source}") }
            return
        }

        val settings = current.settings
        val apiKey = secureProviderKeyStore.load()
        val paid = settings.aiProvider == AiProvider.OpenAI && !apiKey.isNullOrBlank()
        val today = LocalDate.now().toString()
        val runtimeWithBudgetDay = current.runtime.resetAiBudgetIfNeeded(today)
        if (paid && runtimeWithBudgetDay.aiBudgetSpentCents + OPENAI_ACTION_ESTIMATE_CENTS > settings.aiDailyBudgetCents) {
            val runtime = runtimeWithBudgetDay.copy(aiQueueStatus = "Budget paused")
            persistRuntime(runtime)
            _state.update { it.copy(runtime = runtime, message = "AI budget reached - action skipped") }
            return
        }

        _state.update { it.copy(message = "Running ${action.label.lowercase()} for this story") }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (paid) {
                    aiEnrichmentClient.runActionWithOpenAi(apiKey!!, story, action)
                } else {
                    aiEnrichmentClient.runActionLocally(story, action)
                }
            }.getOrElse { aiEnrichmentClient.runActionLocally(story, action) }
                .ifBlank { aiEnrichmentClient.runActionLocally(story, action) }
        }

        withContext(Dispatchers.IO) {
            when (action) {
                StoryAiAction.Summary -> storyDao.updateSummary(storyId, result)
                StoryAiAction.Research -> storyDao.updateResearch(storyId, result)
                StoryAiAction.Translation -> storyDao.updateTranslation(storyId, result)
                StoryAiAction.NeutralTitle -> storyDao.updateNeutralTitle(storyId, result)
            }
        }

        recordUsage(
            action = action.label,
            provider = if (paid) "openai" else "local",
            storyTitle = story.title,
            costCents = if (paid) OPENAI_ACTION_ESTIMATE_CENTS else 0,
            tokens = if (paid) aiEnrichmentClient.consumeTokenUsage().total else 0,
        )

        _state.update { state ->
            val runtime = runtimeWithBudgetDay.copy(
                lastAiJobAt = System.currentTimeMillis(),
                aiQueueStatus = "Idle",
                aiBudgetSpentCents = runtimeWithBudgetDay.aiBudgetSpentCents +
                    if (paid) OPENAI_ACTION_ESTIMATE_CENTS else 0,
                aiBudgetDay = today,
            )
            persistRuntime(runtime)
            state.copy(
                runtime = runtime,
                stories = state.stories.map {
                    if (it.id != storyId) {
                        it
                    } else {
                        when (action) {
                            StoryAiAction.Summary -> it.copy(summary = result, aiFieldsAvailable = true)
                            StoryAiAction.Research -> it.copy(research = result, aiFieldsAvailable = true)
                            StoryAiAction.Translation -> it.copy(translation = result, aiFieldsAvailable = true)
                            StoryAiAction.NeutralTitle -> it.copy(neutralTitle = result, aiFieldsAvailable = true)
                        }
                    }
                },
                message = "${action.label} ready",
            )
        }
    }

    /** Fills in AI fields for every story that is still missing them. */
    suspend fun regenerateMissingAi(limit: Int = 20) {
        enrichVisibleStories(limit)
    }

    fun addSchedule(kind: ScheduleKind, hour: Int) {
        _state.update { current ->
            val schedule = ScheduleWorker.defaultSchedule(kind, hour)
            val schedules = current.schedules + schedule
            persistSchedules(schedules)
            current.copy(schedules = schedules, message = "${kind.label} scheduled for ${hour}:00")
        }
    }

    fun toggleSchedule(scheduleId: String) {
        _state.update { current ->
            val schedules = current.schedules.map {
                if (it.id == scheduleId) it.copy(enabled = !it.enabled) else it
            }
            persistSchedules(schedules)
            current.copy(schedules = schedules, message = "Schedule updated")
        }
    }

    fun deleteSchedule(scheduleId: String) {
        ScheduleWorker.cancel(appContext, scheduleId)
        _state.update { current ->
            val schedules = current.schedules.filterNot { it.id == scheduleId }
            persistSchedules(schedules)
            current.copy(schedules = schedules, message = "Schedule removed")
        }
    }

    suspend fun runScheduleNow(scheduleId: String) {
        val schedule = _state.value.schedules.firstOrNull { it.id == scheduleId } ?: return
        when (schedule.kind) {
            ScheduleKind.Refresh -> refreshNow()
            ScheduleKind.MonitorScan -> {
                scanMonitorsNow()
                AlertNotifier(appContext).notifyMatches(
                    matches = _state.value.alertMatches,
                    stories = _state.value.stories,
                )
            }

            ScheduleKind.Digest -> buildDigestNow()
        }
        _state.update { current ->
            val schedules = current.schedules.map {
                if (it.id == scheduleId) it.copy(lastRunAt = System.currentTimeMillis()) else it
            }
            persistSchedules(schedules)
            current.copy(schedules = schedules)
        }
    }

    /** Builds a short digest of the current top stories and posts it as a notification. */
    fun buildDigestNow(): DigestEntry? {
        val current = _state.value
        val alertStoryIds = current.alertMatches.map { it.storyId }.toSet()
        val stories = current.stories
            .filterNot { it.isHidden }
            .sortedWith(
                compareByDescending<NewsStory> { it.isPinned }
                    .thenByDescending { it.id in alertStoryIds }
                    .thenByDescending { it.publishedAt },
            )
            .take(8)
        if (stories.isEmpty()) {
            _state.update { it.copy(message = "No stories to put in a digest") }
            return null
        }
        val createdAt = System.currentTimeMillis()
        val digest = DigestEntry(
            id = "digest-$createdAt",
            createdAt = createdAt,
            title = "Digest - ${stories.size} stories",
            body = stories.joinToString("\n") { "- ${it.displayTitleForDigest()} (${it.source})" },
            storyIds = stories.map { it.id },
        )
        _state.update { state ->
            val digests = (listOf(digest) + state.digests).take(30)
            persistDigests(digests)
            state.copy(digests = digests, message = "Digest ready")
        }
        AlertNotifier(appContext).notifyDigest(digest.title, digest.body)
        return digest
    }

    fun deleteDigest(digestId: String) {
        _state.update { current ->
            val digests = current.digests.filterNot { it.id == digestId }
            persistDigests(digests)
            current.copy(digests = digests, message = "Digest removed")
        }
    }

    fun resetUsage() {
        _state.update { current ->
            persistUsage(emptyList())
            val runtime = current.runtime.copy(aiBudgetSpentCents = 0)
            persistRuntime(runtime)
            current.copy(usageRecords = emptyList(), runtime = runtime, message = "AI usage reset")
        }
    }

    fun clearFetchHistory() {
        repositoryScope.launch {
            fetchHistoryDao.clear()
        }
        _state.update { it.copy(fetchHistory = emptyList(), message = "Fetch history cleared") }
    }

    private fun recordUsage(
        action: String,
        provider: String,
        storyTitle: String,
        costCents: Int,
        tokens: Int = 0,
    ) {
        _state.update { current ->
            val record = AiUsageRecord(
                id = "usage-${System.currentTimeMillis()}-${action.hashCode()}",
                createdAt = System.currentTimeMillis(),
                action = action,
                provider = provider,
                storyTitle = storyTitle,
                costCents = costCents,
                tokens = tokens,
            )
            val records = (listOf(record) + current.usageRecords).take(100)
            persistUsage(records)
            current.copy(usageRecords = records)
        }
    }

    private fun aiEnabledForStory(feedSources: List<FeedSource>, story: NewsStory): Boolean =
        feedSources.firstOrNull { it.title == story.source }?.aiEnabled ?: true

    private fun NewsStory.displayTitleForDigest(): String =
        neutralTitle?.takeIf { it.isNotBlank() } ?: title

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

    /** Under fifteen minutes WorkManager will not run, so a repeating alarm carries it. */
    private fun scheduleRefreshes(cadenceMinutes: Long) {
        RefreshNewsWorker.schedule(appContext, cadenceMinutes.coerceAtLeast(15))
        if (cadenceMinutes < 15) {
            RefreshAlarmReceiver.schedule(appContext, cadenceMinutes)
        } else {
            RefreshAlarmReceiver.cancel(appContext)
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

    private fun persistSchedules(schedules: List<NewsSchedule>) {
        repositoryScope.launch {
            runtimePreferences.saveSchedules(schedules)
        }
    }

    private fun persistDigests(digests: List<DigestEntry>) {
        repositoryScope.launch {
            runtimePreferences.saveDigests(digests)
        }
    }

    private fun persistUsage(records: List<AiUsageRecord>) {
        repositoryScope.launch {
            runtimePreferences.saveUsage(records)
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

    /** AI runs for six hours at a time unless it is switched on again. */
    private const val AI_SESSION_MILLIS = 6L * 60 * 60 * 1000
    private const val OPENAI_ENRICHMENT_ESTIMATE_CENTS = 2
    private const val OPENAI_ACTION_ESTIMATE_CENTS = 1
    private const val HISTORY_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
}
