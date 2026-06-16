package net.secorp.rssreader.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.time.Duration
import java.time.Instant
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onItemClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ItemListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scrollResetTick by viewModel.scrollResetTick.collectAsState()
    // Jump to the top of the list whenever the page changes or the VM signals
    // a scroll reset (e.g. after mark-all-read, where the page index didn't
    // change but the visible items got swapped under us).
    LaunchedEffect(state.pageIndex, scrollResetTick) {
        listState.scrollToItem(0)
    }
    // Local source-of-truth for the TextField. Binding the field directly to
    // a StateFlow round-trips every keystroke through the VM and back, which
    // lags one frame and causes dropped/garbled input on fast typing. The
    // VM still gets every change via onValueChange below.
    var queryInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.searchActive) {
        if (state.searchActive) {
            searchFocusRequester.requestFocus()
        } else {
            // Search closed elsewhere (back press, nav, etc.) — wipe local
            // state so re-opening starts clean.
            queryInput = ""
        }
    }
    // System back closes search instead of leaving the screen, matching the
    // convention every other Android app uses for in-place search bars.
    BackHandler(enabled = state.searchActive) {
        viewModel.closeSearch()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.searchActive) {
                        SearchField(
                            query = queryInput,
                            onQueryChange = {
                                queryInput = it
                                viewModel.setQuery(it)
                            },
                            onImeSearch = { keyboardController?.hide() },
                            focusRequester = searchFocusRequester,
                        )
                    } else {
                        Text(state.title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.searchActive) viewModel.closeSearch() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.searchActive) {
                        if (queryInput.isNotEmpty()) {
                            IconButton(onClick = {
                                queryInput = ""
                                viewModel.setQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        IconButton(onClick = { viewModel.openSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        TextButton(onClick = { viewModel.markAllVisibleRead() }) {
                            Text("Mark all read")
                        }
                        FilterChip(
                            selected = state.onlyUnread,
                            onClick = { viewModel.toggleUnreadOnly() },
                            label = { Text("Unread") },
                            leadingIcon = if (state.onlyUnread) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.totalCount > 0) {
                PaginationBar(
                    pageIndex = state.pageIndex,
                    pageSize = state.pageSize,
                    totalCount = state.totalCount,
                    onlyUnread = state.onlyUnread,
                    canPrev = state.canPrev,
                    canNext = state.canNext,
                    onPrev = { viewModel.prevPage() },
                    onNext = { viewModel.nextPage() },
                )
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .simpleScrollbar(
                        state = listState,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    SwipeableItemRow(
                        item = item,
                        feedTitle = if (state.showSource) state.feedTitles[item.feedId] else null,
                        onClick = { onItemClick(item.id) },
                        onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PaginationBar(
    pageIndex: Int,
    pageSize: Int,
    totalCount: Int,
    onlyUnread: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val from = pageIndex * pageSize + 1
    val to = minOf((pageIndex + 1) * pageSize, totalCount)
    val unit = if (onlyUnread) " unread" else ""
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = canPrev) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous page",
                )
            }
            Text(
                text = "$from–$to of $totalCount$unit",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next page",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onImeSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search items") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list filters live as the user types, so the IME action just
        // closes the keyboard to let them see the results.
        keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
        // Strip the TextField's own background/indicator so it visually blends
        // into the TopAppBar instead of looking like a chip.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItemRow(
    item: FeedItemEntity,
    feedTitle: String?,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Return false so the row snaps back instead of being removed — we're
        // toggling read state, not dismissing. Side-effect the toggle from
        // here because confirmValueChange fires exactly once per completed
        // swipe gesture.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onToggleRead()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val markingRead = !item.isRead
            val bg = if (markingRead) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.tertiaryContainer
            val fg = if (markingRead) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.onTertiaryContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = if (markingRead) "Mark read" else "Mark unread",
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                )
            }
        },
    ) {
        ItemRow(item = item, feedTitle = feedTitle, onClick = onClick)
    }
}

@Composable
private fun ItemRow(
    item: FeedItemEntity,
    feedTitle: String?,
    onClick: () -> Unit,
) {
    val titleColor =
        if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
    val thumbnailAlpha = if (item.isRead) 0.55f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Solid surface so the swipe background only shows on actual swipe,
            // not through the resting row.
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Leading column reserves the same width whether or not the dot is
        // drawn, so titles stay aligned across rows.
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        // Thumbnail slot always reserves the same 64dp square so rows with
        // and without artwork share a baseline. A surfaceVariant placeholder
        // stands in when the item has no thumbnail.
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .alpha(thumbnailAlpha),
        ) {
            if (!item.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val sub = listOfNotNull(
                feedTitle?.takeIf { it.isNotBlank() },
                item.author?.takeIf { it.isNotBlank() },
                item.pubDate?.let { relativeTime(it) },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Lightweight always-visible scrollbar drawn on the right edge of a
 * LazyColumn. Position is index-based (treats every row as equal height),
 * which is a fine approximation for our ~50-item pages even though item
 * heights vary with description + thumbnail presence.
 */
private fun Modifier.simpleScrollbar(
    state: LazyListState,
    color: Color,
    width: Dp = 4.dp,
    minThumbHeightPx: Float = 24f,
): Modifier = drawWithContent {
    drawContent()
    val total = state.layoutInfo.totalItemsCount
    if (total == 0) return@drawWithContent
    val visible = state.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return@drawWithContent

    val firstIdx = visible.first().index
    val lastIdx = visible.last().index
    val startFraction = firstIdx.toFloat() / total
    val endFraction = ((lastIdx + 1).toFloat() / total).coerceAtMost(1f)

    val barHeight = size.height
    val top = startFraction * barHeight
    val thumbHeight = ((endFraction - startFraction) * barHeight).coerceAtLeast(minThumbHeightPx)
    val barWidthPx = width.toPx()

    drawRect(
        color = color,
        topLeft = Offset(size.width - barWidthPx - 2f, top),
        size = Size(barWidthPx, thumbHeight),
    )
}

private fun relativeTime(instant: Instant): String {
    val now = Instant.now()
    val d = Duration.between(instant, now)
    val seconds = d.seconds
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 604_800 -> "${seconds / 86_400}d ago"
        else -> "${seconds / 604_800}w ago"
    }
}
