package net.secorp.rssreader.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.repo.RssRepository

@HiltViewModel
class ArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    rssRepository: RssRepository,
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle.get<Long>("itemId")) {
        "itemId is required"
    }

    val state: StateFlow<FeedItemEntity?> = rssRepository.observeItem(itemId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
}
