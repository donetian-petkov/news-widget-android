package com.ainews.android.network

import com.ainews.android.data.FeedFetchRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

data class FeedFetchOutcome(
    val stories: List<NewsStory>,
    val records: List<FeedFetchRecord>,
)

private const val MAX_PARALLEL_FEEDS = 6

class RssFeedFetcher {
    fun fetchTopStories(
        sources: List<FeedSource> = defaultFeedSources,
        limitPerFeed: Int = 20,
    ): List<NewsStory> = runBlocking { fetchTopStoriesWithHistory(sources, limitPerFeed).stories }

    /**
     * Fetches every enabled feed and also reports how each one went, so the app can show
     * a fetch history and flag sources that keep failing.
     */
    suspend fun fetchTopStoriesWithHistory(
        sources: List<FeedSource> = defaultFeedSources,
        limitPerFeed: Int = 20,
    ): FeedFetchOutcome = coroutineScope {
        // Feeds are fetched together: one slow feed used to hold up every feed behind it,
        // which is how a refresh could sit on "Updating" for minutes.
        val gate = Semaphore(MAX_PARALLEL_FEEDS)
        val results = sources.filter { it.fetchEnabled }.map { source ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val startedAt = System.currentTimeMillis()
                    val result = runCatching { fetchSource(source, limitPerFeed) }
                    val finishedAt = System.currentTimeMillis()
                    val fetched = result.getOrDefault(emptyList())
                    result.exceptionOrNull()?.let { failure ->
                        // Worth keeping: a feed that stops resolving or times out is the
                        // usual reason a refresh comes back with nothing.
                        android.util.Log.w("AiNewsRefresh", "feed ${source.title} failed: $failure")
                    }
                    fetched to FeedFetchRecord(
                        id = "fetch-${source.id}-$finishedAt",
                        feedId = source.id,
                        feedTitle = source.title,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        success = result.isSuccess && fetched.isNotEmpty(),
                        storyCount = fetched.size,
                        message = when {
                            result.isFailure -> result.exceptionOrNull()?.message ?: "Fetch failed"
                            fetched.isEmpty() -> "No stories returned"
                            else -> "OK"
                        },
                    )
                }
            }
        }.awaitAll()

        FeedFetchOutcome(
            stories = results.flatMap { it.first }.sortedByDescending { it.publishedAt },
            records = results.map { it.second },
        )
    }

    private fun fetchSource(source: FeedSource, limit: Int): List<NewsStory> {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
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

private fun String.cleanHtml(): String = cleanFeedText(this)

/**
 * Turns a feed's raw title or description into something a person can read.
 *
 * Feeds hand over three kinds of rubbish. Tags, which are dropped. Their own editing-system
 * placeholders - `[[gallery]]`, `[[img:4956908]]` - which stand for a picture the feed did
 * not send and which showed up on the widget as literal double brackets in the middle of a
 * sentence. And character codes such as `&#8230;` or `&ndash;`, which were printed as-is
 * because only four of them were ever translated.
 */
internal fun cleanFeedText(raw: String): String =
    raw.replace(HTML_TAG, " ")
        .replace(CMS_PLACEHOLDER, " ")
        .decodeHtmlEntities()
        // Some feeds encode their text twice, so a dash arrives as `&amp;ndash;` and one
        // pass only gets it as far as `&ndash;`. The second pass finishes the job but
        // leaves `&`, `<` and `>` alone, so no round of decoding can invent a tag.
        .decodeHtmlEntities(keepMarkupCodes = true)
        .replace(WHITESPACE, " ")
        .trim()

private val HTML_TAG = Regex("<[^>]*>")

/** `[[gallery]]`, `[[img:4958201]]` and friends: a placeholder, never words to read. */
private val CMS_PLACEHOLDER = Regex("""\[\[[^\[\]]{0,60}]]""")

private val WHITESPACE = Regex("\\s+")

private val HTML_ENTITY = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,15});""")

private val NAMED_HTML_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "shy" to "", "zwnj" to "", "zwj" to "",
    "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026",
    "lsquo" to "\u2018", "rsquo" to "\u2019", "sbquo" to "\u201A",
    "ldquo" to "\u201C", "rdquo" to "\u201D", "bdquo" to "\u201E",
    "laquo" to "\u00AB", "raquo" to "\u00BB", "middot" to "\u00B7",
    "bull" to "\u2022", "deg" to "\u00B0", "euro" to "\u20AC",
    "pound" to "\u00A3", "times" to "\u00D7",
)

/**
 * One pass, so a code that decodes into another code is left alone rather than decoded
 * twice - `&amp;lt;` must come out as the text `&lt;`, not as a tag bracket.
 */
private fun String.decodeHtmlEntities(keepMarkupCodes: Boolean = false): String =
    HTML_ENTITY.replace(this) { match ->
        val body = match.groupValues[1]
        val code = when {
            body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)
            body.startsWith("#") -> body.drop(1).toIntOrNull()
            else -> null
        }
        val decoded = when {
            code != null && code in 1..0x10FFFF -> String(Character.toChars(code))
            // Anything unrecognised stays as it was written: better a stray code than a
            // silently mangled word.
            else -> NAMED_HTML_ENTITIES[body.lowercase(Locale.ROOT)]
        }
        when {
            decoded == null -> match.value
            keepMarkupCodes && decoded in MARKUP_CHARACTERS -> match.value
            else -> decoded
        }
    }

/** `&`, `<` and `>`: the three whose decoding could turn text back into markup. */
private val MARKUP_CHARACTERS = setOf("&", "<", ">")

private fun String.extractFirstImageUrl(): String? =
    Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
