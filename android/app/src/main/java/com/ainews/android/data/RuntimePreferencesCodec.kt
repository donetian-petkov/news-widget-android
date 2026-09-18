package com.ainews.android.data

import org.json.JSONArray
import org.json.JSONObject

object RuntimePreferencesCodec {
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
}
