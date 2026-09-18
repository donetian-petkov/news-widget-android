package com.ainews.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY publishedAt DESC")
    fun observeStories(): Flow<List<StoryEntity>>

    @Query("SELECT COUNT(*) FROM stories")
    fun countStories(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStories(stories: List<StoryEntity>)

    @Query("UPDATE stories SET isNew = 0")
    fun clearNewMarkers()

    @Query("UPDATE stories SET isHidden = 1, hiddenAt = :hiddenAt WHERE id = :storyId")
    fun hideStory(storyId: String, hiddenAt: Long)

    @Query("UPDATE stories SET isHidden = 0, hiddenAt = NULL")
    fun restoreHidden()

    @Query("UPDATE stories SET isHidden = 0, hiddenAt = NULL WHERE id = :storyId")
    fun restoreStory(storyId: String)

    @Query("UPDATE stories SET isPinned = :isPinned, pinnedAt = :pinnedAt WHERE id = :storyId")
    fun setPinned(storyId: String, isPinned: Boolean, pinnedAt: Long?)

    @Query("UPDATE stories SET isSaved = :isSaved, savedAt = :savedAt WHERE id = :storyId")
    fun setSaved(storyId: String, isSaved: Boolean, savedAt: Long?)

    @Query("UPDATE stories SET summary = :summary, aiFieldsAvailable = 1 WHERE id = :storyId")
    fun updateSummary(storyId: String, summary: String)

    @Query("UPDATE stories SET research = :research, aiFieldsAvailable = 1 WHERE id = :storyId")
    fun updateResearch(storyId: String, research: String)

    @Query("UPDATE stories SET translation = :translation, aiFieldsAvailable = 1 WHERE id = :storyId")
    fun updateTranslation(storyId: String, translation: String)

    @Query("UPDATE stories SET neutralTitle = :neutralTitle, aiFieldsAvailable = 1 WHERE id = :storyId")
    fun updateNeutralTitle(storyId: String, neutralTitle: String)

    @Query(
        """
        UPDATE stories
        SET aiFieldsAvailable = 1,
            neutralTitle = :neutralTitle,
            translation = :translation,
            research = :research,
            topicLabels = :topicLabels
        WHERE id = :storyId
        """,
    )
    fun updateEnrichment(
        storyId: String,
        neutralTitle: String?,
        translation: String?,
        research: String?,
        topicLabels: String,
    )
}
