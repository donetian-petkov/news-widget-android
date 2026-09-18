package com.ainews.android.data

import org.json.JSONArray
import org.json.JSONObject

object RuntimePreferencesCodec {
    fun stringListToJson(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    fun stringListFromJson(value: String): List<String> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optString(index).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }.getOrDefault(emptyList())

    fun monitorsToJson(monitors: List<NewsMonitor>): String {
        val array = JSONArray()
        monitors.forEach { monitor ->
            array.put(
                JSONObject()
                    .put("id", monitor.id)
                    .put("sentence", monitor.sentence)
                    .put("enabled", monitor.enabled)
                    .put("lastMatchStoryId", monitor.lastMatchStoryId)
                    .put("lastMatchConfidence", monitor.lastMatchConfidence)
                    .put("lastMatchExplanation", monitor.lastMatchExplanation),
            )
        }
        return array.toString()
    }

    fun monitorsFromJson(value: String): List<NewsMonitor> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val sentence = item.optString("sentence").trim()
                    if (sentence.isBlank()) continue
                    add(
                        NewsMonitor(
                            id = item.optString("id").ifBlank { "monitor-${sentence.hashCode()}" },
                            sentence = sentence,
                            enabled = item.optBoolean("enabled", true),
                            lastMatchStoryId = item.optString("lastMatchStoryId").ifBlank { null },
                            lastMatchConfidence = item.optDouble("lastMatchConfidence").takeIf { !it.isNaN() },
                            lastMatchExplanation = item.optString("lastMatchExplanation").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(defaultNewsMonitors)

    fun feedSourcesToJson(feedSources: List<FeedSource>): String {
        val array = JSONArray()
        feedSources.forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("title", source.title)
                    .put("url", source.url)
                    .put("fetchEnabled", source.fetchEnabled)
                    .put("aiEnabled", source.aiEnabled),
            )
        }
        return array.toString()
    }

    fun feedSourcesFromJson(value: String): List<FeedSource> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    if (title.isBlank() || url.isBlank()) continue
                    add(
                        FeedSource(
                            id = item.optString("id").ifBlank { "feed-${url.hashCode()}" },
                            title = title,
                            url = url,
                            fetchEnabled = item.optBoolean("fetchEnabled", true),
                            aiEnabled = item.optBoolean("aiEnabled", true),
                        ),
                    )
                }
            }
        }.getOrDefault(defaultFeedSources)

    fun widgetPresetsToJson(presets: List<WidgetPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("feedSourceId", preset.feedSourceId)
                    .put("feedSourceIds", JSONArray(preset.feedSourceIds))
                    .put("layoutMode", preset.layoutMode.name)
                    .put("backgroundMode", preset.backgroundMode.name)
                    .put("themeMode", preset.themeMode.name)
                    .put("densityMode", preset.densityMode.name)
                    .put("typographyMode", preset.typographyMode.name),
            )
        }
        return array.toString()
    }

    fun widgetPresetsFromJson(value: String): List<WidgetPreset> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        WidgetPreset(
                            id = item.optString("id").ifBlank { "widget-${name.hashCode()}" },
                            name = name,
                            feedSourceId = item.optString("feedSourceId").ifBlank { WIDGET_ALL_FEEDS },
                            feedSourceIds = item.optJSONArray("feedSourceIds")
                                ?.let { array ->
                                    buildList {
                                        for (feedIndex in 0 until array.length()) {
                                            val feedId = array.optString(feedIndex).trim()
                                            if (feedId.isNotBlank()) add(feedId)
                                        }
                                    }
                                }
                                .orEmpty(),
                            layoutMode = item.optString("layoutMode")
                                .let { runCatching { WidgetLayoutMode.valueOf(it) }.getOrNull() }
                                ?: WidgetLayoutMode.Column,
                            backgroundMode = item.optString("backgroundMode")
                                .let { runCatching { WidgetBackgroundMode.valueOf(it) }.getOrNull() }
                                ?: WidgetBackgroundMode.Solid,
                            themeMode = item.optString("themeMode")
                                .let { runCatching { WidgetThemeMode.valueOf(it) }.getOrNull() }
                                ?: WidgetThemeMode.Light,
                            densityMode = item.optString("densityMode")
                                .let { runCatching { WidgetDensityMode.valueOf(it) }.getOrNull() }
                                ?: WidgetDensityMode.Comfortable,
                            typographyMode = item.optString("typographyMode")
                                .let { runCatching { WidgetTypographyMode.valueOf(it) }.getOrNull() }
                                ?: WidgetTypographyMode.Standard,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun schedulesToJson(schedules: List<NewsSchedule>): String {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(
                JSONObject()
                    .put("id", schedule.id)
                    .put("kind", schedule.kind.name)
                    .put("hour", schedule.hour)
                    .put("enabled", schedule.enabled)
                    .put("lastRunAt", schedule.lastRunAt),
            )
        }
        return array.toString()
    }

    fun schedulesFromJson(value: String): List<NewsSchedule> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val kind = runCatching { ScheduleKind.valueOf(item.optString("kind")) }.getOrNull() ?: continue
                    add(
                        NewsSchedule(
                            id = item.optString("id").ifBlank { "schedule-${kind.name}-${item.optInt("hour")}" },
                            kind = kind,
                            hour = item.optInt("hour").coerceIn(0, 23),
                            enabled = item.optBoolean("enabled", true),
                            lastRunAt = item.optLong("lastRunAt").takeIf { it > 0 },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun digestsToJson(digests: List<DigestEntry>): String {
        val array = JSONArray()
        digests.forEach { digest ->
            array.put(
                JSONObject()
                    .put("id", digest.id)
                    .put("createdAt", digest.createdAt)
                    .put("title", digest.title)
                    .put("body", digest.body)
                    .put("storyIds", JSONArray(digest.storyIds)),
            )
        }
        return array.toString()
    }

    fun digestsFromJson(value: String): List<DigestEntry> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val body = item.optString("body")
                    if (body.isBlank()) continue
                    add(
                        DigestEntry(
                            id = item.optString("id").ifBlank { "digest-${item.optLong("createdAt")}" },
                            createdAt = item.optLong("createdAt"),
                            title = item.optString("title").ifBlank { "Digest" },
                            body = body,
                            storyIds = item.optJSONArray("storyIds")
                                ?.let { ids ->
                                    buildList {
                                        for (idIndex in 0 until ids.length()) {
                                            ids.optString(idIndex).takeIf { it.isNotBlank() }?.let(::add)
                                        }
                                    }
                                }
                                .orEmpty(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun usageToJson(records: List<AiUsageRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("createdAt", record.createdAt)
                    .put("action", record.action)
                    .put("provider", record.provider)
                    .put("storyTitle", record.storyTitle)
                    .put("costCents", record.costCents),
            )
        }
        return array.toString()
    }

    fun usageFromJson(value: String): List<AiUsageRecord> =
        runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val action = item.optString("action")
                    if (action.isBlank()) continue
                    add(
                        AiUsageRecord(
                            id = item.optString("id").ifBlank { "usage-${item.optLong("createdAt")}-$index" },
                            createdAt = item.optLong("createdAt"),
                            action = action,
                            provider = item.optString("provider").ifBlank { "local" },
                            storyTitle = item.optString("storyTitle"),
                            costCents = item.optInt("costCents"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
}
