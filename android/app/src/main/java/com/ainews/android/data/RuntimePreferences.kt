package com.ainews.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.runtimeDataStore by preferencesDataStore(name = "runtime")

class RuntimePreferences(
    private val context: Context,
) {
    val runtimeState: Flow<Pair<RuntimeState, RuntimeSettings>> =
        context.runtimeDataStore.data.map { prefs ->
            val runtime = RuntimeState(
                runtimeEnabled = prefs[Keys.runtimeEnabled] ?: true,
                fetchEnabled = prefs[Keys.fetchEnabled] ?: true,
                aiEnabled = prefs[Keys.aiEnabled] ?: true,
                autoPowerOffAt = prefs[Keys.autoPowerOffAt]?.takeIf { it > 0 },
                lastFetchStartedAt = prefs[Keys.lastFetchStartedAt]?.takeIf { it > 0 },
                lastFetchFinishedAt = prefs[Keys.lastFetchFinishedAt]?.takeIf { it > 0 },
                lastFetchStatus = prefs[Keys.lastFetchStatus]
                    ?.let { runCatching { FetchStatus.valueOf(it) }.getOrNull() }
                    ?: FetchStatus.Idle,
                lastAiJobAt = prefs[Keys.lastAiJobAt]?.takeIf { it > 0 },
                aiQueueStatus = prefs[Keys.aiQueueStatus] ?: "Idle",
            )
            val settings = RuntimeSettings(
                backendMode = prefs[Keys.backendMode]
                    ?.let { runCatching { BackendMode.valueOf(it) }.getOrNull() }
                    ?: BackendMode.NativeRuntime,
                aiProvider = prefs[Keys.aiProvider]
                    ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
                    ?: AiProvider.OpenAI,
                remoteBackendUrl = prefs[Keys.remoteBackendUrl].orEmpty(),
                fetchCadenceMinutes = (prefs[Keys.fetchCadenceMinutes] ?: 30).coerceAtLeast(15),
                monitorScanHour = ((prefs[Keys.monitorScanHour] ?: 20).toInt()).coerceIn(0, 23),
                aiDailyBudgetCents = ((prefs[Keys.aiDailyBudgetCents] ?: 100).toInt()).coerceAtLeast(0),
                providerKeySaved = prefs[Keys.providerKeySaved] ?: false,
            )
            runtime to settings
        }

    suspend fun save(runtime: RuntimeState) {
        context.runtimeDataStore.edit { prefs ->
            prefs[Keys.runtimeEnabled] = runtime.runtimeEnabled
            prefs[Keys.fetchEnabled] = runtime.fetchEnabled
            prefs[Keys.aiEnabled] = runtime.aiEnabled
            prefs[Keys.autoPowerOffAt] = runtime.autoPowerOffAt ?: 0
            prefs[Keys.lastFetchStartedAt] = runtime.lastFetchStartedAt ?: 0
            prefs[Keys.lastFetchFinishedAt] = runtime.lastFetchFinishedAt ?: 0
            prefs[Keys.lastFetchStatus] = runtime.lastFetchStatus.name
            prefs[Keys.lastAiJobAt] = runtime.lastAiJobAt ?: 0
            prefs[Keys.aiQueueStatus] = runtime.aiQueueStatus
        }
    }

    suspend fun saveSettings(settings: RuntimeSettings) {
        context.runtimeDataStore.edit { prefs ->
            prefs[Keys.backendMode] = settings.backendMode.name
            prefs[Keys.aiProvider] = settings.aiProvider.name
            prefs[Keys.remoteBackendUrl] = settings.remoteBackendUrl
            prefs[Keys.fetchCadenceMinutes] = settings.fetchCadenceMinutes.coerceAtLeast(15)
            prefs[Keys.monitorScanHour] = settings.monitorScanHour.toLong().coerceIn(0, 23)
            prefs[Keys.aiDailyBudgetCents] = settings.aiDailyBudgetCents.toLong().coerceAtLeast(0)
            prefs[Keys.providerKeySaved] = settings.providerKeySaved
        }
    }

    private object Keys {
        val runtimeEnabled = booleanPreferencesKey("runtime_enabled")
        val fetchEnabled = booleanPreferencesKey("fetch_enabled")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val autoPowerOffAt = longPreferencesKey("auto_power_off_at")
        val lastFetchStartedAt = longPreferencesKey("last_fetch_started_at")
        val lastFetchFinishedAt = longPreferencesKey("last_fetch_finished_at")
        val lastFetchStatus = stringPreferencesKey("last_fetch_status")
        val lastAiJobAt = longPreferencesKey("last_ai_job_at")
        val aiQueueStatus = stringPreferencesKey("ai_queue_status")
        val backendMode = stringPreferencesKey("backend_mode")
        val aiProvider = stringPreferencesKey("ai_provider")
        val remoteBackendUrl = stringPreferencesKey("remote_backend_url")
        val fetchCadenceMinutes = longPreferencesKey("fetch_cadence_minutes")
        val monitorScanHour = longPreferencesKey("monitor_scan_hour")
        val aiDailyBudgetCents = longPreferencesKey("ai_daily_budget_cents")
        val providerKeySaved = booleanPreferencesKey("provider_key_saved")
    }
}
