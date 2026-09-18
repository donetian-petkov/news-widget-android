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
    fun merge(previousStories: List<NewsStory>, fetchedStories: List<NewsStory>): StoryRefreshResult {
        val previousIds = previousStories.map { it.id }.toSet()
        val fetchedIds = fetchedStories.map { it.id }.toSet()
        val hiddenById = previousStories.associateBy({ it.id }, { it.isHidden to it.hiddenAt })
        val sameStorySet = fetchedIds == previousIds
        val mergedStories = fetchedStories.map { story ->
            val hiddenState = hiddenById[story.id]
            story.copy(
                isNew = !sameStorySet && story.id !in previousIds,
                isHidden = hiddenState?.first ?: false,
                hiddenAt = hiddenState?.second,
            )
        }
        return StoryRefreshResult(mergedStories, sameStorySet)
    }
}
