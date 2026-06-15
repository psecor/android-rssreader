package net.secorp.rssreader.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import net.secorp.rssreader.data.db.entity.PendingActionEntity

@Dao
interface PendingActionDao {
    @Upsert
    suspend fun upsert(action: PendingActionEntity)

    @Query("SELECT * FROM pending_actions ORDER BY queuedAt ASC")
    suspend fun all(): List<PendingActionEntity>

    /**
     * Drop the row only if no newer toggle has landed on the same item.
     * The worker calls this after a successful push so that a fresher user
     * action isn't silently discarded mid-flight.
     */
    @Query("DELETE FROM pending_actions WHERE itemId = :itemId AND queuedAt = :queuedAt")
    suspend fun deleteIfUnchanged(itemId: Long, queuedAt: Instant): Int

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int
}
