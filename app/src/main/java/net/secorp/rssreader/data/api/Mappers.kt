package net.secorp.rssreader.data.api

import net.secorp.rssreader.data.api.dto.CategoryDto
import net.secorp.rssreader.data.api.dto.FeedDto
import net.secorp.rssreader.data.api.dto.FeedItemDto
import net.secorp.rssreader.data.db.entity.CategoryEntity
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity

fun CategoryDto.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun FeedDto.toEntity(): FeedEntity =
    FeedEntity(
        id = id,
        title = title,
        url = url,
        description = description,
        categoryId = categoryId,
        lastFetchedAt = lastFetchedAt,
        lastFetchError = lastFetchError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun FeedItemDto.toEntity(): FeedItemEntity =
    FeedItemEntity(
        id = id,
        feedId = feedId,
        title = title,
        link = link,
        description = description,
        contentHtml = contentHtml,
        author = author,
        pubDate = pubDate,
        guid = guid,
        thumbnail = thumbnail,
        createdAt = createdAt,
        isRead = isRead,
        readAt = readAt,
    )
