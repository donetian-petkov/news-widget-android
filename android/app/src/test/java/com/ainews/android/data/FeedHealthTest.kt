package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedHealthTest {
    private val feed = FeedSource(id = "feed-a", title = "Feed A", url = "https://a.example/rss")

    @Test
    fun countsConsecutiveFailuresSinceLastSuccess() {
        val state = NewsUiState(
            feedSources = listOf(feed),
            fetchHistory = listOf(
                record(finishedAt = 300, success = false, message = "timeout"),
                record(finishedAt = 200, success = false, message = "timeout"),
                record(finishedAt = 100, success = true, storyCount = 4),
            ),
        )

        val health = state.feedHealth.single()

        assertEquals(2, health.consecutiveFailures)
        assertEquals(100L, health.lastSuccessAt)
        assertFalse(health.healthy)
        assertTrue(health.statusText.contains("Failing"))
    }

    @Test
    fun healthyFeedReportsLastStoryCount() {
        val state = NewsUiState(
            feedSources = listOf(feed),
            fetchHistory = listOf(record(finishedAt = 500, success = true, storyCount = 7)),
        )

        val health = state.feedHealth.single()

        assertTrue(health.healthy)
        assertEquals("7 stories last fetch", health.statusText)
    }

    @Test
    fun feedWithoutHistoryReportsNeverFetched() {
        val health = NewsUiState(feedSources = listOf(feed)).feedHealth.single()

        assertEquals("Never fetched", health.statusText)
    }

    private fun record(
        finishedAt: Long,
        success: Boolean,
        storyCount: Int = 0,
        message: String = "OK",
    ): FeedFetchRecord =
        FeedFetchRecord(
            id = "record-$finishedAt",
            feedId = feed.id,
            feedTitle = feed.title,
            startedAt = finishedAt - 50,
            finishedAt = finishedAt,
            success = success,
            storyCount = storyCount,
            message = message,
        )
}
