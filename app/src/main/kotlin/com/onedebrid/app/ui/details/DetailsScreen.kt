package com.onedebrid.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onNavigateToPlayer: (MediaType, String, String?, StreamCandidate?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.navigateToPlayer.collectLatest { navArgs ->
            val mediaType = uiState.media?.type ?: MediaType.MOVIE
            onNavigateToPlayer(
                mediaType,
                navArgs.mediaId,
                navArgs.episodeId,
                navArgs.preferredSource
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.media?.title ?: "Details") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoadingMedia -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.mediaError != null -> {
                    ErrorContent(
                        message = errorMessage(uiState.mediaError!!),
                        onRetry = viewModel::retryMedia,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.media != null -> {
                    MediaContent(
                        media = uiState.media!!,
                        episodes = uiState.episodes,
                        isLoadingEpisodes = uiState.isLoadingEpisodes,
                        episodesError = uiState.episodesError?.let { errorMessage(it) },
                        onPlayMovie = viewModel::onPlayMovie,
                        onChooseStreamMovie = { viewModel.onChooseStream(null) },
                        onPlayEpisode = viewModel::onPlayEpisode,
                        onChooseStreamEpisode = { episode -> viewModel.onChooseStream(episode) },
                        onRetryEpisodes = viewModel::retryEpisodes
                    )
                }
            }

            if (uiState.picker !is PickerUiState.Closed) {
                StreamPickerBottomSheet(
                    pickerState = uiState.picker,
                    onDismiss = viewModel::onDismissPicker,
                    onCandidateSelected = viewModel::onCandidateSelected,
                    onRetry = viewModel::retryChooseStream
                )
            }
        }
    }
}

@Composable
private fun MediaContent(
    media: Media,
    episodes: List<Episode>,
    isLoadingEpisodes: Boolean,
    episodesError: String?,
    onPlayMovie: () -> Unit,
    onChooseStreamMovie: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onChooseStreamEpisode: (Episode) -> Unit,
    onRetryEpisodes: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(text = media.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            media.overview?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (media.type == MediaType.MOVIE) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onPlayMovie, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.padding(8.dp))
                    OutlinedButton(onClick = onChooseStreamMovie, modifier = Modifier.weight(1f)) {
                        Text("Choose Stream")
                    }
                }
            }
        }

        if (media.type == MediaType.TV_SHOW) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Episodes", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoadingEpisodes) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (episodesError != null) {
                item {
                    ErrorContent(message = episodesError, onRetry = onRetryEpisodes)
                }
            } else {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onPlay = { onPlayEpisode(episode) },
                        onChooseStream = { onChooseStreamEpisode(episode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onPlay: () -> Unit,
    onChooseStream: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.seasonNumber}E${episode.episodeNumber} - ${episode.title}",
                style = MaterialTheme.typography.bodyLarge
            )
            episode.overview?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        OutlinedButton(onClick = onChooseStream) {
            Text("Source")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamPickerBottomSheet(
    pickerState: PickerUiState,
    onDismiss: () -> Unit,
    onCandidateSelected: (StreamCandidate) -> Unit,
    onRetry: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Select Stream",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (pickerState) {
                is PickerUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is PickerUiState.Error -> {
                    ErrorContent(
                        message = errorMessage(pickerState.error),
                        onRetry = onRetry
                    )
                }
                is PickerUiState.Loaded -> {
                    if (pickerState.candidates.isEmpty()) {
                        Text(
                            text = "No streams available for this item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            items(pickerState.candidates) { candidate ->
                                StreamCandidateRow(
                                    candidate = candidate,
                                    onClick = { onCandidateSelected(candidate) }
                                )
                            }
                        }
                    }
                }
                PickerUiState.Closed -> {}
            }
        }
    }
}

@Composable
private fun StreamCandidateRow(
    candidate: StreamCandidate,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quality Badge (e.g. 4K, 1080p, HD)
                QualityBadge(candidate = candidate)

                // Provider Badge if available (e.g. RealDebrid, TorBox)
                candidate.providerName?.let { provider ->
                    Badge(text = provider, isPrimary = false)
                }

                // File size if available
                candidate.sizeBytes?.let { size ->
                    Text(
                        text = formatFileSize(size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clean, primary title
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QualityBadge(candidate: StreamCandidate) {
    val qualityText = when {
        candidate.title.contains("2160p", ignoreCase = true) || candidate.title.contains("4K", ignoreCase = true) -> "4K"
        candidate.title.contains("1080p", ignoreCase = true) -> "1080p"
        candidate.title.contains("720p", ignoreCase = true) -> "720p"
        else -> "SD"
    }

    Badge(text = qualityText, isPrimary = true)
}

@Composable
private fun Badge(text: String, isPrimary: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPrimary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return ""
    val gb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1.0) {
        "%.1f GB".format(gb)
    } else {
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        "%.0f MB".format(mb)
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun errorMessage(error: AppError): String = when (error) {
    is AppError.NoCachedStreamAvailable -> stringResource(R.string.player_error_no_cached_stream)
    is AppError.StreamResolutionFailed -> stringResource(R.string.player_error_resolution_failed)
    is AppError.NotAuthenticated -> stringResource(R.string.player_error_not_authenticated)
    is AppError.NoNetworkConnection -> stringResource(R.string.player_error_no_network)
    is AppError.AllProvidersUnavailable -> stringResource(R.string.player_error_providers_unavailable)
    is AppError.LocalStorageError -> stringResource(R.string.player_error_generic)
    is AppError.Unknown -> stringResource(R.string.player_error_generic)
}
