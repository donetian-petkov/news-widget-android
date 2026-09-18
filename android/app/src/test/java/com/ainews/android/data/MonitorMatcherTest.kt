package com.ainews.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorMatcherTest {
    @Test
    fun matchesFluVaccinationAvailabilityFromTitleAndSummaryOnly() {
        val monitor = NewsMonitor(
            id = "flu-bg",
            sentence = "when flu vaccinations will be available to the public in Bulgaria",
        )
        val story = NewsStory(
            id = "story",
            source = "Health",
            sourceUrl = "https://example.com/story",
            publishedAt = 1,
            fetchedAt = 1,
            title = "Flu vaccination calendar expected in Bulgaria",
            summary = "Officials said the public schedule should be available soon.",
            topicLabels = listOf("Public health"),
            aiFieldsAvailable = false,
            isNew = false,
        )

        val decision = MonitorMatcher.evaluate(monitor, story)

        assertTrue(decision.matched)
        assertTrue(decision.confidence > 0.5)
    }

    @Test
    fun ignoresUnrelatedStory() {
        val monitor = NewsMonitor(
            id = "flu-bg",
            sentence = "when flu vaccinations will be available to the public in Bulgaria",
        )
        val story = NewsStory(
            id = "story",
            source = "Energy",
            sourceUrl = "https://example.com/story",
            publishedAt = 1,
            fetchedAt = 1,
            title = "Grid operators prepare winter upgrades",
            summary = "Substation work continues near demand corridors.",
            topicLabels = listOf("Energy"),
            aiFieldsAvailable = false,
            isNew = false,
        )

        val decision = MonitorMatcher.evaluate(monitor, story)

        assertFalse(decision.matched)
    }
}
