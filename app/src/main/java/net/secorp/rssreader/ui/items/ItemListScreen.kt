package net.secorp.rssreader.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.feed?.title ?: "Items") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(items = state.items, key = { it.id }) { item ->
                ItemRow(item = item, onClick = { onItemClick(item.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ItemRow(item: FeedItemEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!item.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
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
