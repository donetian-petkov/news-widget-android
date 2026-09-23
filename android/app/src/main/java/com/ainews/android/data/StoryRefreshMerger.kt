package com.ainews.android.data

data class StoryRefreshResult(
    val stories: List<NewsStory>,
    val sameStorySet: Boolean,
) {
    val newCount: Int
        get() = stories.count { it.isNew }

    val message: String
        get() = if (sameStorySet) {
            "Feed refreshed - no new stories"
        } else {
            "Feed refreshed - $newCount new"
        }
}

object StoryRefreshMerger {
    /**
     * @param clearExistingNew a refresh the reader asked for themselves. Everything already
     * on the widget has just been looked at, so it stops being new; only what the refresh
     * actually brings back is.
     */
    fun merge(
        previousStories: List<NewsStory>,
        fetchedStories: List<NewsStory>,
        clearExistingNew: Boolean = false,
    ): StoryRefreshResult {
        val previousIds = previousStories.map { it.id }.toSet()
        val fetchedIds = fetchedStories.map { it.id }.toSet()
        val hiddenById = previousStories.associateBy({ it.id }, { it.isHidden to it.hiddenAt })
        val pinnedById = previousStories.associateBy({ it.id }, { it.isPinned to it.pinnedAt })
        val newById = previousStories.associateBy({ it.id }, { it.isNew })
        // When a story was first seen, not when it was last downloaded. Every fetch stamps
        // the current time on every story it returns, so without this a story that keeps
        // coming back looks as though it had only just arrived, for ever.
        val firstSeenById = previousStories.associateBy({ it.id }, { it.fetchedAt })
        val sameStorySet = fetchedIds == previousIds
        val mergedStories = fetchedStories.map { story ->
            val hiddenState = hiddenById[story.id]
            val pinnedState = pinnedById[story.id]
            story.copy(
                // A story stays new until it has been read. It used to lose the badge on the
                // next fetch that happened to bring nothing, which is a refresh timer, not
                // the reader: stories went from new to ordinary while nobody was looking.
                isNew = if (clearExistingNew) story.id !in previousIds else newById[story.id] ?: true,
                fetchedAt = firstSeenById[story.id] ?: story.fetchedAt,
                isHidden = hiddenState?.first ?: false,
                hiddenAt = hiddenState?.second,
                isPinned = pinnedState?.first ?: false,
                pinnedAt = pinnedState?.second,
            )
        }
        return StoryRefreshResult(mergedStories, sameStorySet)
    }
}
