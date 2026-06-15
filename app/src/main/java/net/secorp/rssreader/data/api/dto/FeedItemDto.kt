package net.secorp.rssreader.data.api.dto

import java.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class FeedItemDto(
    val id: Long,
    val feedId: Long,
    val title: String,
    val link: String,
    val description: String? = null,
    val contentHtml: String? = null,
    val author: String? = null,
    @Contextual val pubDate: Instant? = null,
    val guid: String,
    val thumbnail: String? = null,
    @Contextual val createdAt: Instant,
    val isRead: Boolean = false,
    @Contextual val readAt: Instant? = null,
)
