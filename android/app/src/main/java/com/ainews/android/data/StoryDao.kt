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
}
