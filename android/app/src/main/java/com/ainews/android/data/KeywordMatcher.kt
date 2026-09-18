package com.ainews.android.data

import java.util.Locale

/**
 * Cheap keyword matching used by the filtered feed. It runs on titles and summaries only,
 * so it can answer immediately after a fetch without waiting for any AI work.
 */
object KeywordMatcher {
    fun normalize(keywords: List<String>): List<String> =
        keywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(40)

    fun matches(keywords: List<String>, story: NewsStory): Boolean =
        matchedKeywords(keywords, story).isNotEmpty()

    fun matchedKeywords(keywords: List<String>, story: NewsStory): List<String> {
        if (keywords.isEmpty()) return emptyList()
        val haystack = listOfNotNull(
            story.title,
            story.summary,
            story.neutralTitle,
        ).joinToString(" ").lowercase(Locale.US)
        return keywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { haystack.contains(it.lowercase(Locale.US)) }
    }
}
