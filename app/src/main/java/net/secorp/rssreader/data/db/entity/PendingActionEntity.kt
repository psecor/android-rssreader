package net.secorp.rssreader.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One queued read/unread mutation per feed item. itemId is the primary key
 * (not auto-generated): if the user toggles the same item twice before the
 * worker drains, the second toggle upserts onto the first — only the latest
 * intent gets pushed to the server, which is the desired behavior.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val itemId: Long,
    val isRead: Boolean,
    val queuedAt: Instant,
)
