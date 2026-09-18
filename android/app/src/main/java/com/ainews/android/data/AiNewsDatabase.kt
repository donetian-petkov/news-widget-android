package com.ainews.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AiNewsDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
}
