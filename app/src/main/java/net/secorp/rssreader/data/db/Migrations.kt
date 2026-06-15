package net.secorp.rssreader.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: introduce the pending_actions table used by the P3 write queue.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_actions (
              itemId INTEGER NOT NULL PRIMARY KEY,
              isRead INTEGER NOT NULL,
              queuedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
