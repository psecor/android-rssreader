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

    fun observeItemsPage(
        feedId: Long?,
        onlyUnread: Boolean,
        query: String = "",
        pageSize: Int,
        pageIndex: Int,
    ): Flow<List<FeedItemEntity>> =
        feedItemDao.observePage(
            feedId = feedId,
            onlyUnread = onlyUnread,
            searchPattern = toLikePattern(query),
            limit = pageSize,
            offset = pageIndex * pageSize,
        )

    fun observeItemsCount(
        feedId: Long?,
        onlyUnread: Boolean,
        query: String = "",
    ): Flow<Int> =
        feedItemDao.observeCount(
            feedId = feedId,
            onlyUnread = onlyUnread,
            searchPattern = toLikePattern(query),
        )

    // Empty query → "%" which matches every row, so the LIKE collapses to
    // a no-op. Keeping the LIKE always-on lets one DAO query serve both
    // filtered and unfiltered fetches.
    private fun toLikePattern(query: String): String =
        if (query.isBlank()) "%" else "%${query.trim()}%"

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
        maxItems: Int = MAX_ITEMS_PER_SYNC,
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
     *
     * Pages through the endpoint until it returns fewer than [pageSize]
     * rows. A single vacation-catch-up mark-all-read on the web easily
     * produces >500 changed rows; without this loop the tail was silently
     * dropped and the cursor advanced past them, losing them forever.
     * Cap of [maxStatuses] guards against a runaway pull if the delta
     * window is truly pathological.
     */
    suspend fun refreshReadStatuses(
        since: Instant?,
        pageSize: Int = 500,
        maxStatuses: Int = MAX_STATUSES_PER_SYNC,
    ) {
        var offset = 0
        var applied = 0
        while (applied < maxStatuses) {
            val page = rssApi.listReadStatuses(
                since = since?.toString(),
                limit = pageSize,
                offset = offset,
            )
            if (page.isEmpty()) break
            for (s in page) {
                feedItemDao.setRead(id = s.feedItemId, isRead = s.isRead, readAt = s.readAt)
            }
            applied += page.size
            if (page.size < pageSize) break
            offset += page.size
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

    private companion object {
        // Soft ceiling on how many items refreshItems pulls in one go. Keeps
        // a clean install from trying to ingest the entire user history on
        // the first sync. Independent of UI page size.
        const val MAX_ITEMS_PER_SYNC = 500

        // Soft ceiling on how many read-status rows to apply in one sync.
        // Much higher than the items cap because it must accommodate two
        // scenarios that both blow past a small limit:
        //  1. Backlog recovery — when a user upgrades from an older version
        //     that lost rows to the pre-pagination bug, resetting the cursor
        //     causes a re-pull of every status row the server has for them,
        //     which can be tens of thousands over the lifetime of an account.
        //  2. Long-vacation catch-up — a web-side bulk mark-all-read on
        //     return from a break can touch several thousand rows in one go.
        // Server orders by updatedAt asc, so hitting this cap would silently
        // drop the *most recent* updates (the ones most likely to be stale)
        // while the cursor advances past them — same bug as before. Keep
        // this high enough that realistic accounts never hit it.
        const val MAX_STATUSES_PER_SYNC = 100_000
    }
}
