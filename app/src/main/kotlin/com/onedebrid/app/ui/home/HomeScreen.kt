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
import com.onedebrid.app.domain.model.WatchedItem
import kotlinx.coroutines.flow.collectLatest

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
 * of a floating debug button, since this is meant to be a real screen. A
 * second top bar action, [onNavigateToSettings], was added in the same
 * session (Session 23) once SettingsScreen.kt existed, using the same
 * TextButton-in-TopAppBar pattern for consistency.
 *
 * Continue Watching rows are tappable to resume playback, as of Session 25.
 *
 * Session 27 change: tapping a row now navigates to Player immediately via
 * nav args (mediaId/episodeId/resumeMs, carried by HomeViewModel's
 * PlayerNavArgs navigation event) — [onNavigateToPlayer] takes those three
 * values instead of no args. There is no more in-screen resolve/error state
 * for the tap itself (the old isResolving spinner and inline resumeError
 * text are both gone) — HomeViewModel no longer resolves a full Media
 * before navigating; PlayerViewModel does that once Player is reached, and
 * any resolution failure is shown there using its own error card + retry
 * instead of here. This was a deliberate, discussed tradeoff (see
 * currentsprint.md Session 27 notes) — tapping a row now always navigates
 * instantly, and returning from a failed resolution requires a back-press
 * rather than staying on Home. R.string.home_resolving_media and
 * R.string.home_resume_error are consequently unused as of this session —
 * left in place with the same "flag rather than silently orphan" handling
 * already established for search_tv_show_unsupported (see that string's
 * own comment in strings.xml and currentsprint.md's Open TODOs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: (mediaId: String, episodeId: String?, resumeMs: Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.navigateToPlayer.collectLatest { navArgs ->
            onNavigateToPlayer(navArgs.mediaId, navArgs.episodeId, navArgs.resumeMs)
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
                    onItemClick = viewModel::onItemClick,
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

/**
 * A single Continue Watching row.
 *
 * Tappable to resume playback as of Session 25. Shows mediaId directly (no
 * title available yet; a successful resolve happens on tap, not
 * proactively for every visible row, since proactively resolving every row
 * would mean an unbounded number of network/cache calls just from Home
 * appearing on screen — deliberately out of scope, left as a possible
 * future enhancement if showing real titles/artwork in the list itself
 * becomes a priority) along with a progress percentage when duration is
 * known. Remove remains available independent of playback resolution.
 *
 * Session 27: no longer takes isResolving — tapping now navigates
 * immediately (see HomeScreen's doc comment), so there is nothing for this
 * row to show mid-resolve anymore.
 */
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