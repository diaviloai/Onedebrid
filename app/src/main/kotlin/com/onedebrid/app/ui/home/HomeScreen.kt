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
import com.onedebrid.app.domain.error.AppError
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
 * Continue Watching rows ARE tappable to resume playback, as of Session
 * 25's Media cache/lookup layer (MediaCache + cache-first wiring in
 * MediaRepositoryImpl, GetMediaByIdUseCase). Tapping a row triggers
 * HomeViewModel.onItemClick(), which resolves the row's bare mediaId to a
 * full Media, builds a PlaybackRequest (including resumePositionMs from
 * the row's own progress), populates PendingPlaybackHolder, and emits a
 * one-shot navigation event this composable collects below to call
 * [onNavigateToPlayer].
 *
 * Known, deliberate limitation carried forward from this change: since
 * MetadataProvider is still StubMetadataProvider (see
 * currentsprint.md), a tap will succeed only if this mediaId happens to
 * already be cache-hit (nothing seeds the cache yet outside of a prior
 * successful fetch), and will otherwise resolve to
 * AppError.AllProvidersUnavailable — surfaced via [uiState].resumeError,
 * rendered inline on the row rather than silently doing nothing, matching
 * the project's "flag rather than silently work around" convention
 * (same one SearchScreen established for its own TV_SHOW rows). This is
 * expected wiring-ahead-of-provider behavior, not a bug in this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.navigateToPlayer.collectLatest {
            onNavigateToPlayer()
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
                    resolvingMediaId = uiState.resolvingMediaId,
                    resumeError = uiState.resumeError,
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
    resolvingMediaId: String?,
    resumeError: AppError?,
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
                    isResolving = item.mediaId == resolvingMediaId,
                    // resumeError doesn't identify which row it belongs to
                    // (HomeUiState only tracks the single most recent
                    // failure, not one per mediaId), so it's shown on
                    // whichever row was most recently tapped and is no
                    // longer resolving. Once resolvingMediaId is cleared,
                    // its former value is gone from state — so this
                    // approximates "the row that just failed" via
                    // resolvingMediaId's absence rather than an exact
                    // per-row error map. Acceptable for a single
                    // concurrent resolution at a time (see
                    // HomeViewModel.onItemClick's doc comment); would need
                    // a proper per-row error map if concurrent resolution
                    // is ever supported.
                    onClick = { onItemClick(item) },
                    onRemove = onRemove
                )
            }
        }
        if (resumeError != null && resolvingMediaId == null) {
            Text(
                text = stringResource(R.string.home_resume_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * A single Continue Watching row.
 *
 * Tappable to resume playback as of Session 25 — see the HomeScreen doc
 * comment. Shows mediaId directly (no title available yet; a successful
 * resolve happens on tap, not proactively for every visible row, since
 * proactively resolving every row would mean an unbounded number of
 * network/cache calls just from Home appearing on screen — deliberately
 * out of scope for this session, left as a possible future enhancement if
 * showing real titles/artwork in the list itself becomes a priority)
 * along with a progress percentage when duration is known. Remove remains
 * available independent of the row's resolve state.
 *
 * [isResolving] shows a small inline spinner in place of the progress
 * text while this specific row's tap is being resolved, and disables
 * further taps on it (click handling itself is guarded in
 * HomeViewModel.onItemClick, this is the visual counterpart).
 */
@Composable
private fun ContinueWatchingRow(
    item: WatchedItem,
    isResolving: Boolean,
    onClick: () -> Unit,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.mediaId,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isResolving) {
                Text(
                    text = stringResource(R.string.home_resolving_media),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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