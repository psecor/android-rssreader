package net.secorp.rssreader.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "feeds",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class FeedEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val url: String,
    val description: String?,
    val categoryId: Long,
    val lastFetchedAt: Instant?,
    val lastFetchError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
