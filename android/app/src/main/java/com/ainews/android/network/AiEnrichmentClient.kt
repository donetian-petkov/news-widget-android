package com.ainews.android.network

import com.ainews.android.data.NewsStory
import com.ainews.android.data.StoryAiAction
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
) {
    val total: Int get() = inputTokens + outputTokens
}

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

    /** Runs one AI action for one story and returns just that block of text. */
    fun runActionWithOpenAi(apiKey: String, story: NewsStory, action: StoryAiAction): String {
        val instruction = when (action) {
            StoryAiAction.Summary -> "Write a short factual summary in at most three sentences."
            StoryAiAction.Research ->
                "Write short research notes: the context a reader needs and what to watch next. Use at most four sentences."
            StoryAiAction.Translation ->
                "Translate the title and summary into English if they are not English, otherwise into Bulgarian."
            StoryAiAction.NeutralTitle ->
                "Rewrite the headline so it is neutral and free of loaded wording. Answer with the headline only."
        }

        val input = """
            $instruction
            Use only the title and summary below. Answer with plain text and no markdown.

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
        val json = JSONObject(responseText)
        lastTokenUsage = json.optJSONObject("usage")?.let { usage ->
            TokenUsage(
                inputTokens = usage.optInt("input_tokens"),
                outputTokens = usage.optInt("output_tokens"),
            )
        } ?: TokenUsage(0, 0)
        return json.optString("output_text").trim()
    }

    /** Token counts reported by the last provider call, so usage can show real numbers. */
    @Volatile
    var lastTokenUsage: TokenUsage = TokenUsage(0, 0)
        private set

    fun consumeTokenUsage(): TokenUsage {
        val usage = lastTokenUsage
        lastTokenUsage = TokenUsage(0, 0)
        return usage
    }

    fun runActionLocally(story: NewsStory, action: StoryAiAction): String =
        when (action) {
            StoryAiAction.Summary -> story.summary.take(400)
            StoryAiAction.Research -> "Local notes: ${story.summary.take(220)} (source: ${story.source})"
            StoryAiAction.Translation -> "Local mode has no translation provider configured."
            StoryAiAction.NeutralTitle -> story.title.removePrefix("Breaking:").trim()
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
