package com.onedebrid.app.ui.home

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.domain.model.WatchedItem

/**
 * The Home screen — Continue Watching, per UI_UX_Design.md's "Home Hub"
 * (Continue Watching row + Watchlist row are both described there; only
 * Continue Watching is implemented so far, since Watchlist has no backing
 * use case or repository support yet — see currentsprint.md Open TODOs).
 *
 * Replaces the inline placeholder that previously lived directly in
 * NavGraph.kt (Session 21–22). The placeholder's one piece of real
 * functionality — a button to reach Search — is preserved here as
 * [onNavigateToSearch], now presented as a proper top bar action instead
 * of a floating debug button, since this is meant to be a real screen.
 *
 * Known, deliberate limitation (Session 23), same "flag rather than
 * silently work around" convention SearchScreen established for its
 * TV_SHOW rows:
 *
 * Continue Watching rows are NOT tappable to resume playback. WatchedItem
 * (see that file's doc comment) only carries a mediaId, episode context,
 * and progress — never a full Media object (title, artwork, etc.).
 * PlaybackRequest requires a full Media. There is no Media cache/lookup
 * layer yet to turn a bare mediaId back into a Media (tracked as Next
 * Steps item 5 in currentsprint.md, unstarted as of this session). Until
 * that lookup layer exists, resuming from Home is not possible — rows
 * display mediaId and progress only, with a Remove action, and are
 * visibly non-interactive rather than silently doing nothing on tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            actions = {
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
                ContinueWatchingRow(item = item, onRemove = onRemove)
            }
        }
    }
}

/**
 * A single Continue Watching row.
 *
 * Not clickable — see the HomeScreen doc comment for why. Shows mediaId
 * directly (no title available yet, per WatchedItem's own doc comment)
 * along with a progress percentage when duration is known, so the row is
 * still informative even without metadata. Remove is the only available
 * action.
 */
@Composable
private fun ContinueWatchingRow(item: WatchedItem, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                    text = stringResource(R.string.home_continue_watching_progress, progressPercent),
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

/**
 * Computes progress as a rounded percentage (0-100). Returns null when
 * either positionMs or durationMs is missing (Recently Played entries
 * have both null per WatchedItem's doc comment, though this screen only
 * ever receives Continue Watching entries today) or when durationMs is
 * zero, to avoid a divide-by-zero.
 */
private fun continueWatchingProgressPercent(item: WatchedItem): Int? {
    val position = item.positionMs ?: return null
    val duration = item.durationMs ?: return null
    if (duration <= 0L) return null
    return ((position.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
}