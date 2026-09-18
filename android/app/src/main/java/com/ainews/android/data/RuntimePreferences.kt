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
    val runtimeState: Flow<RuntimeState> =
        context.runtimeDataStore.data.map { prefs ->
            RuntimeState(
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
    }
}
