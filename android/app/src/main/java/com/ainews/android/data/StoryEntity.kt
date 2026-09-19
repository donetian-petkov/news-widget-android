package com.ainews.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceUrl: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val topicLabels: String,
    val aiFieldsAvailable: Boolean,
    val isNew: Boolean,
    val isHidden: Boolean,
    val hiddenAt: Long?,
    val isPinned: Boolean,
    val pinnedAt: Long?,
    val isSaved: Boolean,
    val savedAt: Long?,
    val isRead: Boolean,
    val readAt: Long?,
    val neutralTitle: String?,
    val translation: String?,
    val research: String?,
)

fun StoryEntity.toModel(): NewsStory =
    NewsStory(
        id = id,
        source = source,
        sourceUrl = sourceUrl,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
        title = title,
        summary = summary,
        imageUrl = imageUrl,
        topicLabels = topicLabels.split("|").filter { it.isNotBlank() },
        aiFieldsAvailable = aiFieldsAvailable,
        isNew = isNew,
        isHidden = isHidden,
        hiddenAt = hiddenAt,
        isPinned = isPinned,
        pinnedAt = pinnedAt,
        isSaved = isSaved,
        savedAt = savedAt,
        isRead = isRead,
        readAt = readAt,
        neutralTitle = neutralTitle,
        translation = translation,
        research = research,
    )

fun NewsStory.toEntity(): StoryEntity =
    StoryEntity(
        id = id,
        source = source,
        sourceUrl = sourceUrl,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
        title = title,
        summary = summary,
        imageUrl = imageUrl,
        topicLabels = topicLabels.joinToString("|"),
        aiFieldsAvailable = aiFieldsAvailable,
        isNew = isNew,
        isHidden = isHidden,
        hiddenAt = hiddenAt,
        isPinned = isPinned,
        pinnedAt = pinnedAt,
        isSaved = isSaved,
        savedAt = savedAt,
        isRead = isRead,
        readAt = readAt,
        neutralTitle = neutralTitle,
        translation = translation,
        research = research,
    )
