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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.repo.RssRepository
import net.secorp.rssreader.data.sync.SyncScheduler

data class ItemListUiState(
    val title: String = "Items",
    val items: List<FeedItemEntity> = emptyList(),
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rssRepository: RssRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    /** feedId is null when this VM is hosting the "All items" destination. */
    private val feedId: Long? = savedStateHandle.get<Long>("feedId")

    private val _onlyUnread = MutableStateFlow(false)
    val onlyUnread: StateFlow<Boolean> = _onlyUnread.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    private val _query = MutableStateFlow("")
    private val _pageIndex = MutableStateFlow(0)

    private val feedFlow = rssRepository.observeFeeds()

    private val filterInputs = combine(
        feedFlow,
        _onlyUnread,
        _searchActive,
        _query,
    ) { feeds, unread, searchActive, query ->
        FilterInputs(feeds = feeds, onlyUnread = unread, searchActive = searchActive, query = query)
    }

    private val totalCount: StateFlow<Int> = filterInputs
        .flatMapLatest { inputs ->
            rssRepository.observeItemsCount(
                feedId = feedId,
                onlyUnread = inputs.onlyUnread,
                query = effectiveQuery(inputs),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    init {
        // If a mark-all-read or sync shrinks the result set so that the
        // current page would be empty, clamp to the last valid page.
        // Filter changes already reset pageIndex to 0 via the setters.
        viewModelScope.launch {
            totalCount.collect { count ->
                val maxIndex = if (count == 0) 0 else (count - 1) / PAGE_SIZE
                if (_pageIndex.value > maxIndex) _pageIndex.value = maxIndex
            }
        }
    }

    val state: StateFlow<ItemListUiState> = combine(
        filterInputs,
        _pageIndex,
        totalCount,
    ) { inputs, pageIndex, count ->
        Triple(inputs, pageIndex, count)
    }
        .flatMapLatest { (inputs, pageIndex, count) ->
            val title = feedTitle(inputs.feeds)
            rssRepository.observeItemsPage(
                feedId = feedId,
                onlyUnread = inputs.onlyUnread,
                query = effectiveQuery(inputs),
                pageSize = PAGE_SIZE,
                pageIndex = pageIndex,
            ).map { items ->
                ItemListUiState(
                    title = title,
                    items = items,
                    onlyUnread = inputs.onlyUnread,
                    searchActive = inputs.searchActive,
                    query = inputs.query,
                    pageIndex = pageIndex,
                    pageSize = PAGE_SIZE,
                    totalCount = count,
                )
            }
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

    private data class FilterInputs(
        val feeds: List<FeedEntity>,
        val onlyUnread: Boolean,
        val searchActive: Boolean,
        val query: String,
    )

    private fun effectiveQuery(inputs: FilterInputs): String =
        if (inputs.searchActive) inputs.query else ""

    private fun feedTitle(feeds: List<FeedEntity>): String =
        if (feedId == null) "All items"
        else feeds.firstOrNull { it.id == feedId }?.title ?: "Items"

    fun toggleUnreadOnly() {
        _onlyUnread.value = !_onlyUnread.value
        _pageIndex.value = 0
    }

    fun refresh() = syncScheduler.enqueueOneShot()

    fun setRead(itemId: Long, isRead: Boolean) {
        viewModelScope.launch { rssRepository.markRead(itemId, isRead = isRead) }
    }

    /**
     * Mark every unread item on the current page as read. The page bounds the
     * action, matching the web app's "mark this page read" semantics.
     */
    fun markAllVisibleRead() {
        val ids = state.value.items.filter { !it.isRead }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { rssRepository.markRead(ids, isRead = true) }
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
