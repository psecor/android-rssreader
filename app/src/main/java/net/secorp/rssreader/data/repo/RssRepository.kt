package net.secorp.rssreader.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.api.RssApi
import net.secorp.rssreader.data.api.toEntity
import net.secorp.rssreader.data.db.dao.CategoryDao
import net.secorp.rssreader.data.db.dao.FeedDao
import net.secorp.rssreader.data.db.dao.FeedItemDao
import net.secorp.rssreader.data.db.dao.FeedWithUnread
import net.secorp.rssreader.data.db.entity.CategoryEntity
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@Singleton
class RssRepository @Inject constructor(
    private val rssApi: RssApi,
    private val categoryDao: CategoryDao,
    private val feedDao: FeedDao,
    private val feedItemDao: FeedItemDao,
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeAll()

    fun observeFeedsWithUnread(): Flow<List<FeedWithUnread>> = feedDao.observeAllWithUnread()

    fun observeFeedsByCategory(categoryId: Long): Flow<List<FeedEntity>> =
        feedDao.observeByCategory(categoryId)

    fun observeItems(
        feedId: Long?,
        onlyUnread: Boolean,
        limit: Int = DEFAULT_ITEM_LIMIT,
    ): Flow<List<FeedItemEntity>> =
        feedItemDao.observe(feedId = feedId, onlyUnread = onlyUnread, limit = limit)

    fun observeTotalUnread(): Flow<Int> = feedItemDao.observeTotalUnread()

    fun observeItem(id: Long): Flow<FeedItemEntity?> = feedItemDao.observeById(id)

    suspend fun refreshCategories() {
        val entities = rssApi.listCategories().map { it.toEntity() }
        categoryDao.replaceAll(entities)
    }

    suspend fun refreshFeeds() {
        val entities = rssApi.listFeeds().map { it.toEntity() }
        feedDao.replaceAll(entities)
    }

    /**
     * Pages through `/api/feed-items` until the server returns fewer than
     * [pageSize] items, then upserts into Room. We do not delete missing
     * items in P2 — the backend rarely removes items, and a full delete
     * pass would race with future write-queue mutations (P3). Cascade from
     * feed-replace handles items whose parent feed disappeared.
     */
    suspend fun refreshItems(pageSize: Int = 200, maxItems: Int = DEFAULT_ITEM_LIMIT) {
        val collected = mutableListOf<FeedItemEntity>()
        var offset = 0
        while (collected.size < maxItems) {
            val page = rssApi.listItems(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            collected += page.map { it.toEntity() }
            if (page.size < pageSize) break
            offset += page.size
        }
        if (collected.isNotEmpty()) {
            feedItemDao.upsertAll(collected)
        }
    }

    /**
     * Order matters: categories first (no FKs), then feeds (FK → categories),
     * then items (FK → feeds). Doing it in the other order would either
     * violate the FK constraints during replace or briefly leave the UI
     * pointed at half-resolved data.
     */
    suspend fun refreshAll() {
        refreshCategories()
        refreshFeeds()
        refreshItems()
    }

    companion object {
        /** Hard cap on the size of any single item list emitted to the UI. */
        const val DEFAULT_ITEM_LIMIT = 500
    }
}
