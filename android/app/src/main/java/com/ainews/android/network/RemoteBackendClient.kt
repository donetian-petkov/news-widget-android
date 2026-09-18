package com.ainews.android.network

import com.ainews.android.data.NewsStory
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class RemoteBackendClient {
    fun fetchStories(baseUrl: String): List<NewsStory> {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return emptyList()

        val candidates = listOf(
            trimmed,
            "$trimmed/api/stories",
            "$trimmed/stories",
        ).distinct()

        return candidates.firstNotNullOfOrNull { url ->
            runCatching { fetchJson(url).toStories() }.getOrNull()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun fetchJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AI-News-Android/0.1")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun String.toStories(): List<NewsStory> {
        val root = trim()
        val array = if (root.startsWith("[")) {
            JSONArray(root)
        } else {
            val obj = JSONObject(root)
            obj.optJSONArray("stories")
                ?: obj.optJSONArray("items")
                ?: obj.optJSONArray("data")
                ?: JSONArray()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val sourceUrl = item.optString("sourceUrl", item.optString("url")).trim()
                if (title.isBlank() || sourceUrl.isBlank()) continue
                val id = item.optString("id").ifBlank { "remote-${sourceUrl.hashCode()}" }
                val labels = item.optJSONArray("topicLabels")
                    ?: item.optJSONArray("topics")
                    ?: JSONArray()
                add(
                    NewsStory(
                        id = id,
                        source = item.optString("source", "Remote backend"),
                        sourceUrl = sourceUrl,
                        publishedAt = item.optLong("publishedAt", System.currentTimeMillis()),
                        fetchedAt = System.currentTimeMillis(),
                        title = title,
                        summary = item.optString("summary", title),
                        imageUrl = item.optString("imageUrl").ifBlank { null },
                        topicLabels = labels.toStringList().ifEmpty { listOf("Remote") },
                        aiFieldsAvailable = item.optBoolean("aiFieldsAvailable", false),
                        isNew = true,
                        neutralTitle = item.optString("neutralTitle").ifBlank { null },
                        translation = item.optString("translation").ifBlank { null },
                        research = item.optString("research").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
}
