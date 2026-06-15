package net.secorp.rssreader.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "feed_items",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("feedId"),
        Index("feedId", "pubDate"),
    ],
)
data class FeedItemEntity(
    @PrimaryKey val id: Long,
    val feedId: Long,
    val title: String,
    val link: String,
    val description: String?,
    val contentHtml: String?,
    val author: String?,
    val pubDate: Instant?,
    val guid: String,
    val thumbnail: String?,
    val createdAt: Instant,
    val isRead: Boolean,
    val readAt: Instant?,
)
