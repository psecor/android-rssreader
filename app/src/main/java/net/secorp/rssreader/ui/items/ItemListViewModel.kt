package net.secorp.rssreader.ui.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.prefs.UiPreferencesStore
import net.secorp.rssreader.data.repo.RssRepository
import net.secorp.rssreader.data.sync.SyncScheduler

data class ItemListUiState(
    val title: String = "Items",
    val items: List<FeedItemEntity> = emptyList(),
    val feedTitles: Map<Long, String> = emptyMap(),
    /**
     * True when this VM is rendering the cross-feed "All items" list, so the
     * row should attribute each item to its source feed. On per-feed lists
     * the title bar already names the feed.
     */
    val showSource: Boolean = false,
    val onlyUnread: Boolean = false,
    val searchActive: Boolean = false,
    val query: String = "",
    val pageIndex: Int = 0,
    val pageSize: Int = PAGE_SIZE,
    val totalCount: Int = 0,
) {
    val totalPages: Int get() =
        if (totalCount == 0) 0 else (totalCount - 1) / pageSize + 1
    val canPrev: Boolean get() = pageIndex > 0
    val canNext: Boolean get() = pageIndex < totalPages - 1
}

private const val PAGE_SIZE = 50

/**
 * The visible list is a **snapshot**, not a live Flow off Room. When a user
 * marks an item read (by opening it, toggling from the article header, or
 * swiping the row), the repository emits a [RssRepository.ReadPatch] and this
 * VM mutates the snapshot in place — the row stays put, its style updates to
 * "read", and the LazyColumn's scroll position is preserved. If we observed
 * Room's Flow directly, marking-as-read in Unread-only mode would drop the
 * row and reshuffle everything below the user's finger.
 *
 * Re-snapshot triggers: filter toggle, feed change, search open/close/type,
 * page next/prev, and completion of a pull-to-refresh sync. Background
 * periodic syncs do NOT trigger a re-snapshot — the list stays stable until
 * the user explicitly refreshes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rssRepository: RssRepository,
    private val syncScheduler: SyncScheduler,
    private val uiPreferences: UiPreferencesStore,
) : ViewModel() {

    /** feedId is null when this VM is hosting the "All items" destination. */
    private val feedId: Long? = savedStateHandle.get<Long>("feedId")

    private val _onlyUnread = MutableStateFlow(uiPreferences.onlyUnread)
    val onlyUnread: StateFlow<Boolean> = _onlyUnread.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    private val _query = MutableStateFlow("")
    private val _pageIndex = MutableStateFlow(0)

    /** Bumped after a pull-to-refresh sync completes, to force a re-snapshot. */
    private val _refreshTrigger = MutableStateFlow(0)

    private val _visibleItems = MutableStateFlow<List<FeedItemEntity>>(emptyList())
    private val _visibleTotalCount = MutableStateFlow(0)

    private val feedFlow: StateFlow<List<FeedEntity>> = rssRepository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ItemListUiState> = combine(
        feedFlow,
        _onlyUnread,
        _searchActive,
        _query,
        _pageIndex,
    ) { feeds, unread, searchActive, query, pageIndex ->
        Header(feeds, unread, searchActive, query, pageIndex)
    }.combine(_visibleItems) { header, items -> header to items }
     .combine(_visibleTotalCount) { (header, items), count ->
        ItemListUiState(
            title = feedTitle(header.feeds),
            items = items,
            feedTitles = header.feeds.associate { it.id to it.title },
            showSource = feedId == null,
            onlyUnread = header.onlyUnread,
            searchActive = header.searchActive,
            query = header.query,
            pageIndex = header.pageIndex,
            pageSize = PAGE_SIZE,
            totalCount = count,
        )
     }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItemListUiState(
            title = if (feedId == null) "All items" else "Items",
        ),
    )

    val isRefreshing: StateFlow<Boolean> = syncScheduler.isReadSyncRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    init {
        // Snapshot loader. Any change to filters / page / refresh trigger
        // re-runs the query and replaces both the visible items and the
        // total-count. mapLatest cancels the in-flight load if the user
        // is spamming filter toggles or typing quickly in search.
        combine(
            _onlyUnread,
            _searchActive,
            _query,
            _pageIndex,
            _refreshTrigger,
        ) { unread, searchActive, query, pageIndex, tick ->
            LoadKey(
                onlyUnread = unread,
                searchActive = searchActive,
                query = query,
                pageIndex = pageIndex,
                refreshTick = tick,
            )
        }.mapLatest { key ->
            val effectiveQuery = if (key.searchActive) key.query else ""
            val items = rssRepository.getItemsPage(
                feedId = feedId,
                onlyUnread = key.onlyUnread,
                query = effectiveQuery,
                pageSize = PAGE_SIZE,
                pageIndex = key.pageIndex,
            )
            val count = rssRepository.countItems(
                feedId = feedId,
                onlyUnread = key.onlyUnread,
                query = effectiveQuery,
            )
            _visibleItems.value = items
            _visibleTotalCount.value = count
        }.launchIn(viewModelScope)

        // Patch the visible snapshot in place when a mark-read fires from
        // anywhere (article auto-mark, article toggle, row swipe). No-op if
        // the item isn't currently visible.
        rssRepository.readPatches
            .onEach { patch ->
                _visibleItems.update { list ->
                    if (list.any { it.id == patch.itemId }) {
                        list.map { row ->
                            if (row.id == patch.itemId) {
                                row.copy(isRead = patch.isRead, readAt = patch.readAt)
                            } else row
                        }
                    } else list
                }
            }
            .launchIn(viewModelScope)

        // Fire a re-snapshot when a pull-to-refresh sync completes so any
        // newly-arrived items land in the visible list. isReadSyncRunning
        // only tracks the one-shot sync (not the periodic worker), which is
        // exactly what we want: background syncs stay invisible until the
        // user explicitly asks for a refresh.
        viewModelScope.launch {
            var wasRunning = false
            syncScheduler.isReadSyncRunning.collect { running ->
                if (wasRunning && !running) _refreshTrigger.value += 1
                wasRunning = running
            }
        }

        // Clamp the page index if the count shrinks below the current page
        // (e.g. a refresh pulls in read-status deltas that empty out later
        // pages). Filter changes already reset pageIndex to 0 explicitly.
        viewModelScope.launch {
            _visibleTotalCount.collect { count ->
                val maxIndex = if (count == 0) 0 else (count - 1) / PAGE_SIZE
                if (_pageIndex.value > maxIndex) _pageIndex.value = maxIndex
            }
        }
    }

    private data class Header(
        val feeds: List<FeedEntity>,
        val onlyUnread: Boolean,
        val searchActive: Boolean,
        val query: String,
        val pageIndex: Int,
    )

    private data class LoadKey(
        val onlyUnread: Boolean,
        val searchActive: Boolean,
        val query: String,
        val pageIndex: Int,
        val refreshTick: Int,
    )

    private fun feedTitle(feeds: List<FeedEntity>): String =
        if (feedId == null) "All items"
        else feeds.firstOrNull { it.id == feedId }?.title ?: "Items"

    fun toggleUnreadOnly() {
        val newValue = !_onlyUnread.value
        _onlyUnread.value = newValue
        uiPreferences.onlyUnread = newValue
        _pageIndex.value = 0
    }

    fun refresh() = syncScheduler.enqueueOneShot()

    fun setRead(itemId: Long, isRead: Boolean) {
        viewModelScope.launch { rssRepository.markRead(itemId, isRead = isRead) }
    }

    /**
     * Mark every unread item on the current page as read. Patches the visible
     * snapshot directly rather than firing per-item ReadPatches — the bulk
     * repo call is a single SQL UPDATE and doesn't emit patches. Rows stay
     * in place styled as read; they clear on the next explicit refresh.
     */
    fun markAllVisibleRead() {
        val ids = _visibleItems.value.filter { !it.isRead }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            rssRepository.markRead(ids, isRead = true)
            val now = java.time.Instant.now()
            _visibleItems.update { list ->
                list.map { row ->
                    if (row.id in ids) row.copy(isRead = true, readAt = now) else row
                }
            }
        }
    }

    fun openSearch() {
        _searchActive.value = true
        _pageIndex.value = 0
    }

    fun closeSearch() {
        _searchActive.value = false
        _query.value = ""
        _pageIndex.value = 0
    }

    fun setQuery(value: String) {
        _query.value = value
        _pageIndex.value = 0
    }

    fun nextPage() {
        if (state.value.canNext) _pageIndex.value = _pageIndex.value + 1
    }

    fun prevPage() {
        if (state.value.canPrev) _pageIndex.value = _pageIndex.value - 1
    }
}
