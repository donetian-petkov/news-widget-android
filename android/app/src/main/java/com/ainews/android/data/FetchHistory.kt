package com.ainews.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fetch_history")
data class FetchHistoryEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val feedTitle: String,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val storyCount: Int,
    val message: String,
)

@Dao
interface FetchHistoryDao {
    @Query("SELECT * FROM fetch_history ORDER BY finishedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<FetchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(records: List<FetchHistoryEntity>)

    @Query("DELETE FROM fetch_history WHERE finishedAt < :olderThan")
    fun prune(olderThan: Long)

    @Query("DELETE FROM fetch_history")
    fun clear()
}

fun FetchHistoryEntity.toModel(): FeedFetchRecord =
    FeedFetchRecord(
        id = id,
        feedId = feedId,
        feedTitle = feedTitle,
        startedAt = startedAt,
        finishedAt = finishedAt,
        success = success,
        storyCount = storyCount,
        message = message,
    )

fun FeedFetchRecord.toEntity(): FetchHistoryEntity =
    FetchHistoryEntity(
        id = id,
        feedId = feedId,
        feedTitle = feedTitle,
        startedAt = startedAt,
        finishedAt = finishedAt,
        success = success,
        storyCount = storyCount,
        message = message,
    )
