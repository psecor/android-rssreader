package net.secorp.rssreader.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test.db"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RssDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingRowsAndCreatesPendingActions() {
        // Populate a v1 DB with realistic data across every existing table.
        // Row-count and value assertions after the migration are the only
        // signal that catches "the migration ran but silently reshaped or
        // dropped something."
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO categories (id, name, createdAt, updatedAt) VALUES
                  (1, 'News', 1000, 1000),
                  (2, 'Tech', 1001, 1001)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO feeds (id, title, url, description, categoryId,
                                    lastFetchedAt, lastFetchError, createdAt, updatedAt) VALUES
                  (10, 'BBC News', 'https://bbc.co.uk/rss',            NULL,  1, 2000, NULL,      1500, 1500),
                  (11, 'HN',       'https://news.ycombinator.com/rss', 'top', 2, NULL, 'timeout', 1501, 1501)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO feed_items (id, feedId, title, link, description, contentHtml,
                                        author, pubDate, guid, thumbnail, createdAt, isRead, readAt) VALUES
                  (100, 10, 'Breaking', 'https://bbc.co.uk/1', NULL,   NULL,        NULL,   3000, 'g1', NULL,                2500, 0, NULL),
                  (101, 10, 'Read one', 'https://bbc.co.uk/2', 'desc', '<p>hi</p>', 'BBC',  3001, 'g2', 'https://img/1.jpg', 2501, 1, 3100),
                  (102, 11, 'HN post',  'https://news.yc/x',   NULL,   NULL,        'sama', 3050, 'g3', NULL,                2510, 0, NULL)
                """.trimIndent()
            )
        }

        // runMigrationsAndValidate both applies MIGRATION_1_2 and validates
        // the resulting schema against the exported v2 snapshot. Any missing
        // or misshaped column, index, or FK on either side of the migration
        // fails this call before the assertion block below runs.
        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MIGRATION_1_2,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM categories").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM feeds").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM feed_items").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3, c.getInt(0))
            }
            db.query("SELECT title, isRead, readAt FROM feed_items WHERE id = 101").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Read one", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(3100L, c.getLong(2))
            }
            // pending_actions was introduced in v2 and starts empty.
            db.query("SELECT COUNT(*) FROM pending_actions").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
