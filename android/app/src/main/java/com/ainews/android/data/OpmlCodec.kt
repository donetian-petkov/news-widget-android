package com.ainews.android.data

/**
 * Reads and writes the OPML subscription lists that most feed readers export,
 * so feeds can move in and out of the app without retyping every URL.
 */
object OpmlCodec {
    private val outlineRegex = Regex("""<outline\b[^>]*>""", RegexOption.IGNORE_CASE)

    fun parse(opml: String): List<FeedSource> {
        if (!opml.contains("<outline", ignoreCase = true)) return emptyList()
        return outlineRegex.findAll(opml)
            .mapNotNull { match ->
                val outline = match.value
                val url = outline.attribute("xmlUrl")?.trim().orEmpty()
                if (url.isBlank()) return@mapNotNull null
                val title = listOfNotNull(outline.attribute("title"), outline.attribute("text"))
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?: url
                FeedSource(
                    id = "feed-${url.hashCode().toUInt()}",
                    title = title,
                    url = url,
                )
            }
            .distinctBy { it.url.trim().lowercase() }
            .toList()
    }

    fun write(feedSources: List<FeedSource>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("<opml version=\"2.0\">")
        appendLine("  <head><title>AI News feeds</title></head>")
        appendLine("  <body>")
        feedSources.forEach { source ->
            appendLine(
                "    <outline type=\"rss\" text=\"${source.title.escapeXml()}\" " +
                    "title=\"${source.title.escapeXml()}\" xmlUrl=\"${source.url.escapeXml()}\" />",
            )
        }
        appendLine("  </body>")
        append("</opml>")
    }

    private fun String.attribute(name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.unescapeXml()

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun String.unescapeXml(): String =
        replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
}
