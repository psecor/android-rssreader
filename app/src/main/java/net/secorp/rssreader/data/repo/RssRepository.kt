package net.secorp.rssreader.data.repo

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.secorp.rssreader.data.api.RssApi
import net.secorp.rssreader.data.api.toEntity
import net.secorp.rssreader.data.db.dao.CategoryDao
import net.secorp.rssreader.data.db.dao.FeedDao
import net.secorp.rssreader.data.db.dao.FeedItemDao
import net.secorp.rssreader.data.db.dao.FeedWithUnread
import net.secorp.rssreader.data.db.dao.PendingActionDao
import net.secorp.rssreader.data.db.entity.CategoryEntity
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.db.entity.PendingActionEntity
import net.secorp.rssreader.data.sync.SyncScheduler
import net.secorp.rssreader.data.sync.SyncStateStore

@Singleton
class RssRepository @Inject constructor(
    private val rssApi: RssApi,
    private val categoryDao: CategoryDao,
    private val feedDao: FeedDao,
    private val feedItemDao: FeedItemDao,
    private val pendingActionDao: PendingActionDao,
    private val syncScheduler: SyncScheduler,
    private val syncStateStore: SyncStateStore,
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeFeeds(): Flow<List<FeedEntity>> = feedDao.observeAll()

    fun observeFeedsWithUnread(): Flow<List<FeedWithUnread>> = feedDao.observeAllWithUnread()

    fun observeFeedsByCategory(categoryId: Long): Flow<List<FeedEntity>> =
        feedDao.observeByCategory(categoryId)

    fun observeItems(
        feedId: Long?,
        onlyUnread: Boolean,
        query: String = "",
        limit: Int = DEFAULT_ITEM_LIMIT,
    ): Flow<List<FeedItemEntity>> {
        // Empty query → "%" which matches every row, so the LIKE collapses to
        // a no-op. Keeping the LIKE always-on avoids two DAO methods.
        val pattern = if (query.isBlank()) "%" else "%${query.trim()}%"
        return feedItemDao.observe(
            feedId = feedId,
            onlyUnread = onlyUnread,
            searchPattern = pattern,
            limit = limit,
        )
    }

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
     * [pageSize] items, then upserts into Room. When [since] is non-null,
     * the server only returns items with createdAt > since — this is the
     * delta path, and the result will normally be small.
     */
    suspend fun refreshItems(
        pageSize: Int = 200,
        maxItems: Int = DEFAULT_ITEM_LIMIT,
        since: Instant? = null,
    ) {
        val collected = mutableListOf<FeedItemEntity>()
        var offset = 0
        while (collected.size < maxItems) {
            val page = rssApi.listItems(
                limit = pageSize,
                offset = offset,
                since = since?.toString(),
            )
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
     * Pulls read-status rows touched since [since] and applies them onto
     * local feed items. Items the local DB doesn't have yet are ignored
     * (UPDATE matches zero rows) — they'll come in through future item
     * syncs if they're still within the window.
     */
    suspend fun refreshReadStatuses(since: Instant?) {
        val statuses = rssApi.listReadStatuses(since = since?.toString())
        for (s in statuses) {
            feedItemDao.setRead(id = s.feedItemId, isRead = s.isRead, readAt = s.readAt)
        }
    }

    /**
     * Order matters: categories first (no FKs), then feeds (FK → categories),
     * then items (FK → feeds). Doing it in the other order would either
     * violate the FK constraints during replace or briefly leave the UI
     * pointed at half-resolved data.
     *
     * On any sync, the high-water cursor is captured BEFORE the network
     * calls. The next sync uses that as `since=`, so anything created or
     * modified during the sync window itself gets caught next time. We
     * accept a small amount of re-fetch over the risk of missing updates.
     */
    suspend fun refreshAll() {
        val cursor = syncStateStore.getLastSyncedAt()
        val nextCursor = Instant.now()

        refreshCategories()
        refreshFeeds()
        refreshItems(since = cursor)
        // Read-status sync is always delta — fetching every user's full
        // history on a clean install would be wasteful. cursor==null means
        // "from the beginning of time", which the server interprets correctly.
        refreshReadStatuses(since = cursor)

        syncStateStore.setLastSyncedAt(nextCursor)
    }

    /**
     * Updates Room immediately so the UI reflects the change before any
     * network call, and enqueues a pending action for the WriteSyncWorker
     * to push to the server. Upsert on itemId means toggling the same item
     * twice quickly produces one push, not two.
     */
    suspend fun markRead(itemId: Long, isRead: Boolean) {
        val now = Instant.now()
        feedItemDao.setRead(itemId, isRead = isRead, readAt = if (isRead) now else null)
        pendingActionDao.upsert(
            PendingActionEntity(itemId = itemId, isRead = isRead, queuedAt = now)
        )
        syncScheduler.enqueueWritePush()
    }

    /**
     * Bulk variant for "mark all read" — single SQL UPDATE for the items,
     * single Upsert batch for the pending actions, one write push. No-op on
     * empty input.
     */
    suspend fun markRead(itemIds: List<Long>, isRead: Boolean) {
        if (itemIds.isEmpty()) return
        val now = Instant.now()
        val readAt = if (isRead) now else null
        feedItemDao.setReadMany(itemIds, isRead = isRead, readAt = readAt)
        pendingActionDao.upsertAll(
            itemIds.map { PendingActionEntity(itemId = it, isRead = isRead, queuedAt = now) }
        )
        syncScheduler.enqueueWritePush()
    }

    companion object {
        /** Hard cap on the size of any single item list emitted to the UI. */
        const val DEFAULT_ITEM_LIMIT = 500
    }
}
