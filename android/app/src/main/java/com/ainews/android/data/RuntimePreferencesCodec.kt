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
                    .put("url", source.url),
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
                    .put("densityMode", preset.densityMode.name),
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
}
