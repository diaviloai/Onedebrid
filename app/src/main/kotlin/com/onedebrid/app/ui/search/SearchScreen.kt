package com.onedebrid.app.ui.search

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.coordinator.SearchState
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult

/**
 * The Search screen.
 *
 * As of Session 26, tapping ANY result (movie or TV show) navigates to
 * Details via [onNavigateToDetails], passing only the result's mediaId —
 * this screen no longer builds a PlaybackRequest or touches
 * PendingPlaybackHolder itself. DetailsViewModel re-fetches the full Media
 * via GetMediaByIdUseCase and, for TV shows, the episode list via
 * GetEpisodesUseCase, then handles the PendingPlaybackHolder handoff to
 * Player from there. See DetailsScreen.kt/DetailsViewModel.kt for that
 * flow and NavGraph.kt for the route wiring.
 *
 * This closes the "TV_SHOW not yet playable" gap this screen had prior to
 * Session 26 (PlaybackRequest requires an Episode for TV_SHOW content,
 * SearchResult/Media carried no episode data, and no episode-picker screen
 * existed yet) by giving every result type a real destination instead of
 * disabling half of them.
 *
 * One remaining known, deliberate limitation, unchanged from before: manual
 * stream-source selection isn't reachable from anywhere yet (Details' play
 * actions, like this screen's old direct-to-Player flow, always build
 * PlaybackRequest with preferredSource = null, i.e. Smart Defaults). This
 * matches Project_Design.md's Smart Defaults principle as the correct
 * default behavior, not just a shortcut taken to avoid building a picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetails: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            colors = TextFieldDefaults.colors(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { submitSearch(query, viewModel) }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val searchState = uiState.searchState) {
                is SearchState.Idle -> IdleContent(
                    history = uiState.searchHistory,
                    onHistoryItemClick = { historyQuery ->
                        query = historyQuery
                        submitSearch(historyQuery, viewModel)
                    },
                    onClearHistory = viewModel::clearHistory
                )

                is SearchState.Searching -> LoadingContent()

                is SearchState.Results -> ResultsContent(
                    results = searchState.results,
                    onResultClick = { searchResult ->
                        onNavigateToDetails(searchResult.media.id)
                    }
                )

                is SearchState.Error -> ErrorContent(
                    error = searchState.error,
                    onRetry = { submitSearch(query, viewModel) }
                )
            }
        }
    }
}

/**
 * Submits a search if the query is non-blank, matching the general
 * principle of not sending empty queries to the search system needlessly.
 */
private fun submitSearch(query: String, viewModel: SearchViewModel) {
    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
        viewModel.search(trimmed)
    }
}

@Composable
private fun IdleContent(
    history: List<String>,
    onHistoryItemClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_idle_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = onClearHistory) {
                Text(stringResource(R.string.search_history_clear))
            }
        }
        LazyColumn {
            items(history) { historyQuery ->
                Text(
                    text = historyQuery,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHistoryItemClick(historyQuery) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
private fun ResultsContent(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(results) { result ->
            SearchResultRow(result = result, onClick = onResultClick)
        }
    }
}

/**
 * A single search result row.
 *
 * As of Session 26, every result is tappable — MOVIE and TV_SHOW alike —
 * since both now have a real destination (Details). Prior to this session,
 * TV_SHOW rows were rendered non-interactive with a "Not yet supported"
 * label; that label and the isPlayable split are gone now that the
 * limitation they existed to flag no longer applies.
 */
@Composable
private fun SearchResultRow(result: SearchResult, onClick: (SearchResult) -> Unit) {
    val media: Media = result.media

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(result) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyLarge
            )
            val subtitle = buildString {
                media.year?.let { append(it) }
                if (media.type == MediaType.TV_SHOW) {
                    if (isNotEmpty()) append(" • ")
                    append(stringResource(R.string.search_tv_show_label))
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Error presentation. Reuses the same isRecoverable split as PlayerScreen's
 * ErrorContent (see that file for the full reasoning) rather than
 * inventing a second convention for the same AppError type.
 */
@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    val message = searchErrorMessage(error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (error.isRecoverable) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.player_retry))
            }
        }
    }
}

/**
 * User-facing copy for each AppError case relevant to search.
 *
 * Kept local to this screen, same convention as PlayerScreen's
 * errorMessage() — AppError is a domain type and stays presentation-
 * agnostic (Technical_standards.md).
 */
@Composable
private fun searchErrorMessage(error: AppError): String = when (error) {
    is AppError.NoNetworkConnection -> stringResource(R.string.player_error_no_network)
    is AppError.AllProvidersUnavailable -> stringResource(R.string.search_error_providers_unavailable)
    is AppError.NotAuthenticated -> stringResource(R.string.player_error_not_authenticated)
    is AppError.NoCachedStreamAvailable -> stringResource(R.string.player_error_generic)
    is AppError.StreamResolutionFailed -> stringResource(R.string.player_error_generic)
    is AppError.LocalStorageError -> stringResource(R.string.player_error_generic)
    is AppError.Unknown -> stringResource(R.string.player_error_generic)
}