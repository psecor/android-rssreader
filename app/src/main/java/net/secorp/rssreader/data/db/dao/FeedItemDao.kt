package net.secorp.rssreader.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@Dao
interface FeedItemDao {
    /**
     * Paginated observer for the item lists.
     * - feedId == null → all feeds
     * - onlyUnread == true → exclude items where isRead = 1
     * - searchPattern is a LIKE pattern ("%" for no filter, "%foo%" to match).
     *   Caller is responsible for escaping % and _ if the user is allowed to
     *   type them; for v1 we just substring-search the raw input.
     *
     * Paired with [observeCount] so the UI can render "from–to of total".
     * Returning a bounded page keeps the LazyColumn cheap and avoids the
     * Coil thumbnail-load OOM that an unbounded "All items" list used to
     * trigger.
     */
    @Query(
        """
        SELECT * FROM feed_items
        WHERE (:feedId IS NULL OR feedId = :feedId)
          AND (:onlyUnread = 0 OR isRead = 0)
          AND (title LIKE :searchPattern
               OR description LIKE :searchPattern
               OR author LIKE :searchPattern)
        ORDER BY pubDate DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePage(
        feedId: Long?,
        onlyUnread: Boolean,
        searchPattern: String,
        limit: Int,
        offset: Int,
    ): Flow<List<FeedItemEntity>>

    /** Total rows matching the same filter set [observePage] uses. */
    @Query(
        """
        SELECT COUNT(*) FROM feed_items
        WHERE (:feedId IS NULL OR feedId = :feedId)
          AND (:onlyUnread = 0 OR isRead = 0)
          AND (title LIKE :searchPattern
               OR description LIKE :searchPattern
               OR author LIKE :searchPattern)
        """
    )
    fun observeCount(
        feedId: Long?,
        onlyUnread: Boolean,
        searchPattern: String,
    ): Flow<Int>

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

    @Query("UPDATE feed_items SET isRead = :isRead, readAt = :readAt WHERE id = :id")
    suspend fun setRead(id: Long, isRead: Boolean, readAt: java.time.Instant?)

    @Query("UPDATE feed_items SET isRead = :isRead, readAt = :readAt WHERE id IN (:ids)")
    suspend fun setReadMany(ids: List<Long>, isRead: Boolean, readAt: java.time.Instant?)
}
