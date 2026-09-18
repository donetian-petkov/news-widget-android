package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {
    @Test
    fun widgetTitleDefaultsToAllFeeds() {
        val state = NewsUiState()

        assertEquals("All Feeds", state.widgetFeedTitle)
    }

    @Test
    fun widgetStoriesFollowSelectedFeed() {
        val state = NewsUiState(
            settings = RuntimeSettings(widgetFeedSourceId = "bbc-world"),
            stories = listOf(
                story(id = "ai", source = "AI Policy", publishedAt = 20),
                story(id = "bbc", source = "BBC World", publishedAt = 10),
            ),
        )

        assertEquals(listOf("bbc"), state.widgetStories.map { it.id })
        assertEquals("BBC World", state.widgetFeedTitle)
    }

    @Test
    fun widgetStoriesKeepAllFeedsWhenUnset() {
        val state = NewsUiState(
            stories = listOf(
                story(id = "newer", source = "AI Policy", publishedAt = 20),
                story(id = "older", source = "BBC World", publishedAt = 10),
            ),
        )

        assertEquals(listOf("newer", "older"), state.widgetStories.map { it.id })
    }

    private fun story(
        id: String,
        source: String,
        publishedAt: Long,
    ) = NewsStory(
        id = id,
        source = source,
        sourceUrl = "https://example.com/$id",
        publishedAt = publishedAt,
        fetchedAt = publishedAt,
        title = "Title $id",
        summary = "Summary $id",
        topicLabels = listOf("World"),
        aiFieldsAvailable = false,
        isNew = true,
    )
}
