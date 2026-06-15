package net.secorp.rssreader.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.secorp.rssreader.data.db.dao.FeedWithUnread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    onFeedClick: (Long) -> Unit,
    onSignOut: () -> Unit,
    viewModel: FeedListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feeds") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.signOut()
                                    onSignOut()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (state.groups.isEmpty() && state.uncategorized.isEmpty()) {
            EmptyState(modifier = Modifier.padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "cat-${group.category.id}") {
                        CategoryHeader(name = group.category.name)
                    }
                    items(items = group.feeds, key = { "feed-${it.id}" }) { feed ->
                        FeedRow(feed = feed, onClick = { onFeedClick(feed.id) })
                    }
                }
                if (state.uncategorized.isNotEmpty()) {
                    item(key = "cat-uncat") { CategoryHeader(name = "Uncategorized") }
                    items(items = state.uncategorized, key = { "feed-${it.id}" }) { feed ->
                        FeedRow(feed = feed, onClick = { onFeedClick(feed.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
    HorizontalDivider()
}

@Composable
private fun FeedRow(feed: FeedWithUnread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = feed.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (feed.unreadCount > 0) {
            Badge(modifier = Modifier.padding(start = 8.dp)) {
                Text(feed.unreadCount.toString())
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No feeds yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pull or tap the refresh icon to sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

