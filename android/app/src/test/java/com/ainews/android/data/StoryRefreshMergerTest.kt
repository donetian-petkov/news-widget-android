package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRefreshMergerTest {
    @Test
    fun preservesHiddenStateForFetchedStories() {
        val previous = listOf(story("one").copy(isHidden = true, hiddenAt = 42))
        val fetched = listOf(story("one"))

        val result = StoryRefreshMerger.merge(previous, fetched)

        assertTrue(result.sameStorySet)
        assertTrue(result.stories.single().isHidden)
        assertEquals(42L, result.stories.single().hiddenAt)
        assertFalse(result.stories.single().isNew)
    }

    @Test
    fun preservesPinnedStateForFetchedStories() {
        val previous = listOf(story("one").copy(isPinned = true, pinnedAt = 99))
        val fetched = listOf(story("one"))

        val result = StoryRefreshMerger.merge(previous, fetched)

        assertTrue(result.stories.single().isPinned)
        assertEquals(99L, result.stories.single().pinnedAt)
    }

    @Test
    fun marksOnlyNewIdsAsNewWhenStorySetChanges() {
        val previous = listOf(story("one"), story("two"))
        val fetched = listOf(story("two"), story("three"))

        val result = StoryRefreshMerger.merge(previous, fetched)

        assertFalse(result.sameStorySet)
        assertFalse(result.stories.first { it.id == "two" }.isNew)
        assertTrue(result.stories.first { it.id == "three" }.isNew)
        assertEquals(1, result.newCount)
    }

    @Test
    fun aStoryStaysNewWhenALaterFetchBringsNothing() {
        val previous = listOf(story("one").copy(isNew = true))
        val fetched = listOf(story("one"))

        val result = StoryRefreshMerger.merge(previous, fetched)

        assertTrue(result.sameStorySet)
        assertTrue(result.stories.single().isNew)
    }

    @Test
    fun aStoryTheReaderHasSeenDoesNotBecomeNewAgain() {
        val previous = listOf(story("one").copy(isRead = true, isNew = false))
        val fetched = listOf(story("one"), story("two"))

        val result = StoryRefreshMerger.merge(previous, fetched)

        assertFalse(result.stories.first { it.id == "one" }.isNew)
        assertTrue(result.stories.first { it.id == "two" }.isNew)
    }

    private fun story(id: String): NewsStory =
        NewsStory(
            id = id,
            source = "Source",
            sourceUrl = "https://example.com/$id",
            publishedAt = 1,
            fetchedAt = 1,
            title = "Title $id",
            summary = "Summary $id",
            topicLabels = listOf("World"),
            aiFieldsAvailable = false,
            isNew = false,
        )
}
