package net.secorp.rssreader.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.db.entity.FeedEntity

data class FeedWithUnread(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val unreadCount: Int,
)

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE categoryId = :categoryId ORDER BY title COLLATE NOCASE ASC")
    fun observeByCategory(categoryId: Long): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): FeedEntity?

    @Query(
        """
        SELECT f.id AS id, f.title AS title, f.categoryId AS categoryId,
               SUM(CASE WHEN fi.id IS NOT NULL AND fi.isRead = 0 THEN 1 ELSE 0 END) AS unreadCount
        FROM feeds f
        LEFT JOIN feed_items fi ON fi.feedId = f.id
        GROUP BY f.id
        ORDER BY f.title COLLATE NOCASE ASC
        """
    )
    fun observeAllWithUnread(): Flow<List<FeedWithUnread>>

    @Upsert
    suspend fun upsertAll(feeds: List<FeedEntity>)

    @Query("DELETE FROM feeds WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<Long>)

    @Transaction
    suspend fun replaceAll(feeds: List<FeedEntity>) {
        upsertAll(feeds)
        deleteMissing(feeds.map { it.id })
    }
}
