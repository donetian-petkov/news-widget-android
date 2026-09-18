package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSourceTest {
    @Test
    fun mergeWithDefaultFeedSourcesAddsMacDefaultsAndKeepsCustomFeeds() {
        val oldSavedFeeds = listOf(
            FeedSource(
                id = "bbc-world-old",
                title = "BBC World",
                url = "http://feeds.bbci.co.uk/news/world/rss.xml",
            ),
            FeedSource(
                id = "custom-ai",
                title = "Custom AI",
                url = "https://example.com/ai.xml",
            ),
        )

        val merged = mergeWithDefaultFeedSources(oldSavedFeeds)

        assertEquals(defaultFeedSources.first(), merged.first())
        assertEquals(defaultFeedSources.size + 1, merged.size)
        assertTrue(merged.any { it.id == "svobodna-tochka" })
        assertTrue(merged.any { it.id == "custom-ai" })
        assertEquals(1, merged.count { it.title == "BBC World" })
    }
}
