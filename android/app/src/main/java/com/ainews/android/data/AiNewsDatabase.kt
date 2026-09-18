package com.ainews.android.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AiNewsDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN pinnedAt INTEGER")
            }
        }
    }
}
