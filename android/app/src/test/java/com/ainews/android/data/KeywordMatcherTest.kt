package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {
    @Test
    fun matchesTitleAndSummaryIgnoringCase() {
        val story = story(title = "Grid upgrades in Sofia", summary = "Operators prepare for winter load")

        assertTrue(KeywordMatcher.matches(listOf("sofia"), story))
        assertTrue(KeywordMatcher.matches(listOf("winter load"), story))
        assertFalse(KeywordMatcher.matches(listOf("vaccination"), story))
    }

    @Test
    fun reportsEveryMatchedKeyword() {
        val story = story(title = "Flu vaccines arrive in Bulgaria", summary = "Pharmacies start next week")

        assertEquals(listOf("flu", "bulgaria"), KeywordMatcher.matchedKeywords(listOf("flu", "bulgaria", "energy"), story))
    }

    @Test
    fun normalizeTrimsBlanksAndDuplicates() {
        assertEquals(listOf("energy", "policy"), KeywordMatcher.normalize(listOf(" energy ", "", "Energy", "policy")))
    }

    @Test
    fun emptyKeywordListMatchesNothing() {
        assertFalse(KeywordMatcher.matches(emptyList(), story(title = "Anything", summary = "Anything")))
    }

    private fun story(title: String, summary: String): NewsStory =
        NewsStory(
            id = "story",
            source = "Source",
            sourceUrl = "https://example.com/story",
            publishedAt = 1,
            fetchedAt = 1,
            title = title,
            summary = summary,
            topicLabels = emptyList(),
            aiFieldsAvailable = false,
            isNew = false,
        )
}
