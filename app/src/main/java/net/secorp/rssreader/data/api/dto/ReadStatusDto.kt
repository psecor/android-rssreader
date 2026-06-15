package net.secorp.rssreader.data.api.dto

import java.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ReadStatusDto(
    val feedItemId: Long,
    val isRead: Boolean,
    @Contextual val readAt: Instant? = null,
    @Contextual val openedAt: Instant? = null,
    @Contextual val updatedAt: Instant,
)
