package com.ainews.android.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StoryEntity::class, FetchHistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AiNewsDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    abstract fun fetchHistoryDao(): FetchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN pinnedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN isSaved INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN savedAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fetch_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        feedId TEXT NOT NULL,
                        feedTitle TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        finishedAt INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        storyCount INTEGER NOT NULL,
                        message TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
