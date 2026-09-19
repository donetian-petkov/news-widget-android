package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySearchTest {
    @Test
    fun searchMatchesEveryWordAcrossTitleAndSummary() {
        val story = story("Grid upgrades in Sofia", "Winter load forecasts rise")

        assertTrue(story.matchesSearch("sofia winter"))
        assertTrue(story.matchesSearch("  GRID  "))
        assertFalse(story.matchesSearch("sofia vaccines"))
    }

    @Test
    fun emptySearchKeepsEverything() {
        assertTrue(story("Anything", "Anything").matchesSearch("   "))
    }

    @Test
    fun feedFiltersSearchAndUnreadTogether() {
        val state = NewsUiState(
            stories = listOf(
                story("Sofia grid", "Winter load").copy(id = "a"),
                story("Sofia grid", "Winter load").copy(id = "b", isRead = true),
                story("Film festival", "Cinema").copy(id = "c"),
            ),
            feedViewMode = FeedViewMode.Unread,
            searchQuery = "sofia",
        )

        assertEquals(listOf("a"), state.visibleStories.map { it.id })
        assertEquals(2, state.unreadCount)
    }

    private fun story(title: String, summary: String): NewsStory =
        NewsStory(
            id = "story-$title",
            source = "Source",
            sourceUrl = "https://example.com",
            publishedAt = 1,
            fetchedAt = 1,
            title = title,
            summary = summary,
            topicLabels = emptyList(),
            aiFieldsAvailable = false,
            isNew = false,
        )
}
