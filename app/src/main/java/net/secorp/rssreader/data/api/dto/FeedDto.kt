package net.secorp.rssreader.data.api.dto

import java.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class FeedDto(
    val id: Long,
    val title: String,
    val url: String,
    val description: String? = null,
    val categoryId: Long,
    @Contextual val lastFetchedAt: Instant? = null,
    val lastFetchError: String? = null,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant,
)
