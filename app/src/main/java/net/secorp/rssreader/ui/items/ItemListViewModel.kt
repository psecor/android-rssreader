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
import kotlinx.coroutines.flow.stateIn
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.repo.RssRepository

data class ItemListUiState(
    val title: String = "Items",
    val items: List<FeedItemEntity> = emptyList(),
    val onlyUnread: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    rssRepository: RssRepository,
) : ViewModel() {

    /** feedId is null when this VM is hosting the "All items" destination. */
    private val feedId: Long? = savedStateHandle.get<Long>("feedId")

    private val _onlyUnread = MutableStateFlow(false)
    val onlyUnread: StateFlow<Boolean> = _onlyUnread.asStateFlow()

    private val feedFlow = rssRepository.observeFeeds()

    val state: StateFlow<ItemListUiState> = combine(
        feedFlow,
        _onlyUnread,
    ) { feeds, unread -> feeds to unread }
        .flatMapLatest { (feeds, unread) ->
            val title = feedTitle(feeds)
            rssRepository.observeItems(feedId = feedId, onlyUnread = unread)
                .let { items ->
                    combine(items, _onlyUnread) { list, u ->
                        ItemListUiState(title = title, items = list, onlyUnread = u)
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ItemListUiState(title = if (feedId == null) "All items" else "Items"),
        )

    private fun feedTitle(feeds: List<FeedEntity>): String =
        if (feedId == null) "All items"
        else feeds.firstOrNull { it.id == feedId }?.title ?: "Items"

    fun toggleUnreadOnly() {
        _onlyUnread.value = !_onlyUnread.value
    }
}
