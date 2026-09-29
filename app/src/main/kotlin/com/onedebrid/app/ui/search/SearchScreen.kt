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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
 * this screen no longer builds a PlaybackRequest itself. DetailsViewModel
 * re-fetches the full Media via GetMediaByIdUseCase and, for TV shows, the
 * episode list via GetEpisodesUseCase, then hands off to Player by emitting
 * a PlayerNavArgs and navigating with real nav args.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDetails: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearSearch) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(),
            keyboardActions = KeyboardActions(
                onSearch = {
                    val trimmed = query.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.search(trimmed)
                    }
                }
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (val searchState = uiState.searchState) {
                is SearchState.Idle -> IdleContent(
                    history = uiState.searchHistory,
                    onHistoryItemClick = { historyQuery ->
                        viewModel.search(historyQuery)
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
                    onRetry = {
                        val trimmed = query.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.search(trimmed)
                        }
                    }
                )
            }
        }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    val message = searchErrorMessage(error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Alignment.Center
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
