package net.secorp.rssreader.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@Dao
interface FeedItemDao {
    /**
     * Single parameterized observer for the item lists.
     * - feedId == null → all feeds
     * - onlyUnread == true → exclude items where isRead = 1
     *
     * Always bounded by a LIMIT so the "All items" view can't emit a list
     * with five thousand entries to LazyColumn (which holds the whole list
     * in memory and triggered thumbnail loads through Coil that pushed the
     * device into OOM territory).
     */
    @Query(
        """
        SELECT * FROM feed_items
        WHERE (:feedId IS NULL OR feedId = :feedId)
          AND (:onlyUnread = 0 OR isRead = 0)
        ORDER BY pubDate DESC, id DESC
        LIMIT :limit
        """
    )
    fun observe(feedId: Long?, onlyUnread: Boolean, limit: Int): Flow<List<FeedItemEntity>>

    @Query(
        """
        SELECT fi.* FROM feed_items fi
        INNER JOIN feeds f ON f.id = fi.feedId
        WHERE f.categoryId = :categoryId
        ORDER BY fi.pubDate DESC, fi.id DESC
        """
    )
    fun observeByCategory(categoryId: Long): Flow<List<FeedItemEntity>>

    @Query("SELECT COUNT(*) FROM feed_items WHERE isRead = 0")
    fun observeTotalUnread(): Flow<Int>

    @Query("SELECT * FROM feed_items WHERE id = :id")
    suspend fun getById(id: Long): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE id = :id")
    fun observeById(id: Long): Flow<FeedItemEntity?>

    @Upsert
    suspend fun upsertAll(items: List<FeedItemEntity>)
}
