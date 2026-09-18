package com.ainews.android.network

import com.ainews.android.data.FeedSource
import com.ainews.android.data.NewsStory
import com.ainews.android.data.defaultFeedSources
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RssFeedFetcher {
    fun fetchTopStories(
        sources: List<FeedSource> = defaultFeedSources,
        limitPerFeed: Int = 8,
    ): List<NewsStory> =
        sources.flatMap { source ->
            runCatching { fetchSource(source, limitPerFeed) }.getOrDefault(emptyList())
        }.sortedByDescending { it.publishedAt }

    private fun fetchSource(source: FeedSource, limit: Int): List<NewsStory> {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AI-News-Android/0.1")
        }

        connection.inputStream.use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(stream, null)
            }
            val stories = mutableListOf<NewsStory>()
            var current = MutableRssItem()
            var inItem = false
            var tag: String? = null

            while (parser.eventType != XmlPullParser.END_DOCUMENT && stories.size < limit) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "item" || tag == "entry") {
                            inItem = true
                            current = MutableRssItem()
                        }
                        if (inItem) {
                            when (tag) {
                                "enclosure" -> {
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    val url = parser.getAttributeValue(null, "url").orEmpty()
                                    if (type.startsWith("image/") && url.isNotBlank()) {
                                        current.imageUrl = current.imageUrl.ifBlank { url }
                                    }
                                }

                                "media:content", "content" -> {
                                    val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    val url = parser.getAttributeValue(null, "url").orEmpty()
                                    if ((medium == "image" || type.startsWith("image/")) && url.isNotBlank()) {
                                        current.imageUrl = current.imageUrl.ifBlank { url }
                                    }
                                }

                                "media:thumbnail", "thumbnail" -> {
                                    val url = parser.getAttributeValue(null, "url").orEmpty()
                                    if (url.isNotBlank()) {
                                        current.imageUrl = current.imageUrl.ifBlank { url }
                                    }
                                }

                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href").orEmpty()
                                    if (href.isNotBlank()) {
                                        current.link = current.link.ifBlank { href }
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty().trim()
                            if (text.isNotBlank()) {
                                when (tag) {
                                    "title" -> current.title += text
                                    "description", "summary" -> current.summary += text
                                    "link" -> current.link = current.link.ifBlank { text }
                                    "pubDate", "updated", "published" -> current.publishedAt = text
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if ((parser.name == "item" || parser.name == "entry") && inItem) {
                            current.toStory(source)?.let(stories::add)
                            inItem = false
                        }
                        tag = null
                    }
                }
                parser.next()
            }

            return stories
        }
    }

    private data class MutableRssItem(
        var title: String = "",
        var summary: String = "",
        var link: String = "",
        var publishedAt: String = "",
        var imageUrl: String = "",
    ) {
        fun toStory(source: FeedSource): NewsStory? {
            val cleanTitle = title.cleanHtml().trim()
            val cleanLink = link.trim()
            if (cleanTitle.isBlank() || cleanLink.isBlank()) return null

            val cleanSummary = summary.cleanHtml().trim().ifBlank { cleanTitle }
            val cleanImageUrl = imageUrl.ifBlank { summary.extractFirstImageUrl() }
            val publishedMillis = parsePublishedAt(publishedAt)
            val id = stableId(cleanLink.ifBlank { "$source:$cleanTitle" })

            return NewsStory(
                id = id,
                source = source.title,
                sourceUrl = cleanLink,
                publishedAt = publishedMillis,
                fetchedAt = System.currentTimeMillis(),
                title = cleanTitle,
                summary = cleanSummary,
                imageUrl = cleanImageUrl,
                topicLabels = inferTopicLabels("$cleanTitle $cleanSummary"),
                aiFieldsAvailable = false,
                isNew = true,
            )
        }
    }
}

private fun parsePublishedAt(value: String): Long {
    if (value.isBlank()) return System.currentTimeMillis()
    return runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.recoverCatching {
        ZonedDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
}

private fun stableId(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
    return "rss-$digest"
}

private fun inferTopicLabels(text: String): List<String> {
    val lower = text.lowercase(Locale.US)
    val labels = buildList {
        if (lower.contains("bulgaria") || lower.contains("sofia")) add("Bulgaria")
        if (lower.contains("health") || lower.contains("vaccine") || lower.contains("flu")) add("Public health")
        if (lower.contains("ai ") || lower.contains("artificial intelligence")) add("AI")
        if (lower.contains("policy") || lower.contains("regulation")) add("Policy")
        if (lower.contains("energy") || lower.contains("grid")) add("Energy")
        if (lower.contains("war") || lower.contains("defence") || lower.contains("defense")) add("Security")
        if (lower.contains("market") || lower.contains("economy")) add("Economy")
    }
    return labels.take(3).ifEmpty { listOf("World") }
}

private fun String.cleanHtml(): String =
    replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")

private fun String.extractFirstImageUrl(): String? =
    Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
