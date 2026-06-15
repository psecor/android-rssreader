package net.secorp.rssreader.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.secorp.rssreader.auth.AuthRepository
import net.secorp.rssreader.data.db.dao.FeedWithUnread
import net.secorp.rssreader.data.db.entity.CategoryEntity
import net.secorp.rssreader.data.repo.RssRepository
import net.secorp.rssreader.data.sync.SyncScheduler

data class FeedGroup(
    val category: CategoryEntity,
    val feeds: List<FeedWithUnread>,
)

data class FeedListUiState(
    val totalUnread: Int = 0,
    val groups: List<FeedGroup> = emptyList(),
    val uncategorized: List<FeedWithUnread> = emptyList(),
)

@HiltViewModel
class FeedListViewModel @Inject constructor(
    rssRepository: RssRepository,
    private val syncScheduler: SyncScheduler,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<FeedListUiState> = combine(
        rssRepository.observeCategories(),
        rssRepository.observeFeedsWithUnread(),
        rssRepository.observeTotalUnread(),
    ) { categories, feeds, totalUnread ->
        val feedsByCategory = feeds.groupBy { it.categoryId }
        val groups = categories.map { c ->
            FeedGroup(category = c, feeds = feedsByCategory[c.id].orEmpty())
        }
        val knownCategoryIds = categories.mapTo(mutableSetOf()) { it.id }
        val uncategorized = feeds.filter { it.categoryId !in knownCategoryIds }
        FeedListUiState(
            totalUnread = totalUnread,
            groups = groups,
            uncategorized = uncategorized,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedListUiState(),
    )

    val isRefreshing: StateFlow<Boolean> = syncScheduler.isReadSyncRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun refresh() = syncScheduler.enqueueOneShot()

    fun signOut() = authRepository.signOut()
}
