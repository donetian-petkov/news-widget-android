package com.ainews.android.network

import java.net.HttpURLConnection
import java.net.URL

/** Reads an article page as plain text for research, as the Mac widget does. */
object ArticleText {
    private val SCRIPT_OR_STYLE = Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val ARTICLE = Regex("<article[\\s\\S]*?</article>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val SPACE = Regex("\\s+")

    /** The page text, from its <article> when it has one, cut to [maxChars]; "" when it cannot be read. */
    fun fetch(link: String, maxChars: Int): String {
        if (!link.startsWith("http://") && !link.startsWith("https://")) return ""
        return runCatching {
            val connection = (URL(link).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9_000
                readTimeout = 9_000
                setRequestProperty("User-Agent", "live-news-ai/1.0 (+rss-research)")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching ""
                if (connection.contentType?.contains("text/html") != true) return@runCatching ""
                fromHtml(connection.inputStream.bufferedReader().use { it.readText() }).take(maxChars)
            } finally {
                connection.disconnect()
            }
        }.getOrDefault("")
    }

    fun fromHtml(html: String): String {
        val cleaned = html.replace(SCRIPT_OR_STYLE, " ")
        val chunk = ARTICLE.find(cleaned)?.value ?: cleaned
        return chunk.replace(TAG, " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace(SPACE, " ")
            .trim()
    }
}
