package net.secorp.rssreader.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@Dao
interface FeedItemDao {
    @Query("SELECT * FROM feed_items WHERE feedId = :feedId ORDER BY pubDate DESC, id DESC")
    fun observeByFeed(feedId: Long): Flow<List<FeedItemEntity>>

    @Query(
        """
        SELECT fi.* FROM feed_items fi
        INNER JOIN feeds f ON f.id = fi.feedId
        WHERE f.categoryId = :categoryId
        ORDER BY fi.pubDate DESC, fi.id DESC
        """
    )
    fun observeByCategory(categoryId: Long): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items ORDER BY pubDate DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE id = :id")
    suspend fun getById(id: Long): FeedItemEntity?

    @Query("SELECT * FROM feed_items WHERE id = :id")
    fun observeById(id: Long): Flow<FeedItemEntity?>

    @Upsert
    suspend fun upsertAll(items: List<FeedItemEntity>)
}
