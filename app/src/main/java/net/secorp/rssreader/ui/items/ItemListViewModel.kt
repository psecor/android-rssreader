package net.secorp.rssreader.ui.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.repo.RssRepository

data class ItemListUiState(
    val feed: FeedEntity? = null,
    val items: List<FeedItemEntity> = emptyList(),
)

@HiltViewModel
class ItemListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    rssRepository: RssRepository,
) : ViewModel() {

    private val feedId: Long = checkNotNull(savedStateHandle.get<Long>("feedId")) {
        "feedId is required"
    }

    val state: StateFlow<ItemListUiState> = combine(
        rssRepository.observeFeeds(),
        rssRepository.observeItemsByFeed(feedId),
    ) { feeds, items ->
        ItemListUiState(
            feed = feeds.firstOrNull { it.id == feedId },
            items = items,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ItemListUiState(),
    )
}
