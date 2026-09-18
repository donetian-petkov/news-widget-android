package com.ainews.android.network

import com.ainews.android.data.NewsStory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StoryEnrichment(
    val neutralTitle: String?,
    val translation: String?,
    val research: String?,
    val topicLabels: List<String>,
)

class AiEnrichmentClient {
    fun enrichWithOpenAi(apiKey: String, story: NewsStory): StoryEnrichment {
        val input = """
            Enrich this news story using only the title and summary.
            Return compact JSON with keys neutralTitle, translation, research, topicLabels.
            topicLabels must be useful topical labels, not generic AI markers.

            Title: ${story.title}
            Summary: ${story.summary}
            Source: ${story.source}
        """.trimIndent()

        val body = JSONObject()
            .put("model", "gpt-5")
            .put("input", input)
            .toString()

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val outputText = JSONObject(responseText).optString("output_text")
        return outputText.toEnrichment(story)
    }

    fun enrichLocally(story: NewsStory): StoryEnrichment =
        StoryEnrichment(
            neutralTitle = story.title.removePrefix("Breaking:").trim(),
            translation = "Local enrichment only: no translation provider configured.",
            research = "Local summary: ${story.summary.take(220)}",
            topicLabels = story.topicLabels.ifEmpty { listOf("General") },
        )

    private fun String.toEnrichment(story: NewsStory): StoryEnrichment {
        val cleaned = trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        if (json == null) {
            return StoryEnrichment(
                neutralTitle = story.title,
                translation = cleaned.take(500),
                research = cleaned.take(500),
                topicLabels = story.topicLabels,
            )
        }

        val labels = json.optJSONArray("topicLabels")
        return StoryEnrichment(
            neutralTitle = json.optString("neutralTitle").ifBlank { story.title },
            translation = json.optString("translation").ifBlank { null },
            research = json.optString("research").ifBlank { null },
            topicLabels = buildList {
                if (labels != null) {
                    for (index in 0 until labels.length()) {
                        labels.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.ifEmpty { story.topicLabels },
        )
    }
}
