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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    SwipeableItemRow(
                        item = item,
                        onClick = { onItemClick(item.id) },
                        onToggleRead = { viewModel.setRead(item.id, !item.isRead) },
                    )
                    HorizontalDivider()
                }
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
        ItemRow(item = item, onClick = onClick)
    }
}

@Composable
private fun ItemRow(item: FeedItemEntity, onClick: () -> Unit) {
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
        if (!item.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(thumbnailAlpha),
            )
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
