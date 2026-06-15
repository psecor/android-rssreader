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
)

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

    private val feedFlow = rssRepository.observeFeeds()

    val state: StateFlow<ItemListUiState> = combine(
        feedFlow,
        _onlyUnread,
        _searchActive,
        _query,
    ) { feeds, unread, searchActive, query ->
        FilterInputs(feeds = feeds, onlyUnread = unread, searchActive = searchActive, query = query)
    }
        .flatMapLatest { inputs ->
            val title = feedTitle(inputs.feeds)
            // Empty query when search bar is closed; the DAO collapses an
            // empty query to a "%" no-op LIKE.
            val effectiveQuery = if (inputs.searchActive) inputs.query else ""
            rssRepository.observeItems(
                feedId = feedId,
                onlyUnread = inputs.onlyUnread,
                query = effectiveQuery,
            ).map { items ->
                ItemListUiState(
                    title = title,
                    items = items,
                    onlyUnread = inputs.onlyUnread,
                    searchActive = inputs.searchActive,
                    query = inputs.query,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ItemListUiState(title = if (feedId == null) "All items" else "Items"),
        )

    private data class FilterInputs(
        val feeds: List<FeedEntity>,
        val onlyUnread: Boolean,
        val searchActive: Boolean,
        val query: String,
    )

    val isRefreshing: StateFlow<Boolean> = syncScheduler.isReadSyncRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private fun feedTitle(feeds: List<FeedEntity>): String =
        if (feedId == null) "All items"
        else feeds.firstOrNull { it.id == feedId }?.title ?: "Items"

    fun toggleUnreadOnly() {
        _onlyUnread.value = !_onlyUnread.value
    }

    fun refresh() = syncScheduler.enqueueOneShot()

    fun setRead(itemId: Long, isRead: Boolean) {
        viewModelScope.launch { rssRepository.markRead(itemId, isRead = isRead) }
    }

    /**
     * Mark every unread item currently visible in [state] as read. Bounded
     * by the DAO's LIMIT (DEFAULT_ITEM_LIMIT), so this is "mark the loaded
     * window" rather than "mark every unread item ever".
     */
    fun markAllVisibleRead() {
        val ids = state.value.items.filter { !it.isRead }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { rssRepository.markRead(ids, isRead = true) }
    }

    fun openSearch() {
        _searchActive.value = true
    }

    fun closeSearch() {
        _searchActive.value = false
        _query.value = ""
    }

    fun setQuery(value: String) {
        _query.value = value
    }
}
