package com.onedebrid.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.WatchedItem
import kotlinx.coroutines.flow.collectLatest

/**
 * The Home screen — Continue Watching, per UI_UX_Design.md's "Home Hub".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetails: (MediaType, String, Long?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: (MediaType, String, String?, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.navigateToPlayer.collectLatest { navArgs ->
            onNavigateToPlayer(
                MediaType.MOVIE,
                navArgs.mediaId.toString(),
                navArgs.episodeId,
                navArgs.preferredSource ?: ""
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            actions = {
                TextButton(onClick = onNavigateToSettings) {
                    Text(stringResource(R.string.home_settings_action))
                }
                Button(onClick = onNavigateToSearch) {
                    Text(stringResource(R.string.home_search_action))
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingContent()
                uiState.continueWatching.isEmpty() -> EmptyContent()
                else -> ContinueWatchingList(
                    items = uiState.continueWatching,
                    onItemClick = { item ->
                        onNavigateToDetails(MediaType.MOVIE, item.mediaId, item.positionMs)
                    },
                    onRemove = viewModel::removeItem
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.home_continue_watching_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContinueWatchingList(
    items: List<WatchedItem>,
    onItemClick: (WatchedItem) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.home_continue_watching_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.mediaId }) { item ->
                ContinueWatchingRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    onRemove = onRemove
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    item: WatchedItem,
    onClick: () -> Unit,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.mediaId,
                style = MaterialTheme.typography.bodyLarge
            )
            val progressPercent = continueWatchingProgressPercent(item)
            if (progressPercent != null) {
                Text(
                    text = stringResource(
                        R.string.home_continue_watching_progress,
                        progressPercent
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onRemove(item.mediaId) }) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.home_remove_item)
            )
        }
    }
}

private fun continueWatchingProgressPercent(item: WatchedItem): Int? {
    val position = item.positionMs ?: return null
    val duration = item.durationMs ?: return null
    if (duration <= 0L) return null
    return ((position.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
}
