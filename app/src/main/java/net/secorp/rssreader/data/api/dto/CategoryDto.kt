package net.secorp.rssreader.data.api.dto

import java.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant,
)
