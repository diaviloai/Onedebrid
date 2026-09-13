package com.onedebrid.app.ui.details

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.VideoQuality
import kotlinx.coroutines.flow.collectLatest

/**
 * The Details / Episode-picker screen (Session 26).
 *
 * Reached only from Search — see DetailsViewModel's doc comment for why
 * Continue Watching's tap-to-resume flow is NOT routed through here.
 *
 * Renders one of two bodies depending on Media.type once loaded:
 * - MOVIE: title/overview/year plus a single Play action.
 * - TV_SHOW: same header, plus an episode list grouped by season. Tapping
 *   an episode plays that specific episode.
 *
 * Known, deliberate limitation carried forward from Search's original
 * gap: episodes come from MediaRepository.getEpisodes(), which is wired to
 * StubMetadataProvider today (see currentsprint.md) — so the episode list
 * will show AppError.AllProvidersUnavailable in practice until a real
 * MetadataProvider exists. This is expected wiring-ahead-of-provider
 * behavior, not a bug in this screen, and is surfaced inline (episodesError)
 * rather than silently showing an empty list.
 *
 * Stream-candidate picker feature (added after Session 30): next to Play
 * (movie) and next to each episode row (TV), a secondary "Choose a
 * stream" text action opens PickerSheet — a ModalBottomSheet listing every
 * StreamCandidate GetStreamCandidatesUseCase finds for that target.
 * Selecting one calls viewModel.onCandidateSelected(), which navigates to
 * Player exactly like Play does, just with that specific candidate
 * attached (see DetailsViewModel.onCandidateSelected()'s own doc comment).
 * Play itself is completely unchanged — this is an additive, secondary
 * affordance, per the discussed and confirmed Smart-Default-stays-primary
 * design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onNavigateToPlayer: (
        mediaId: String,
        episodeId: String?,
        resumeMs: Long?,
        preferredSource: StreamCandidate?
    ) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Session 27: navigateToPlayer now carries the nav args
    // Route.Player.build() needs (mediaId/episodeId/resumeMs), replacing
    // PendingPlaybackHolder — see DetailsViewModel.kt's doc comment.
    // resumeMs is always null from this screen (Details has no resume
    // context), unchanged from before this session. preferredSource
    // (stream-candidate picker feature) is non-null only when the
    // navigation event originated from onCandidateSelected() rather than
    // onPlayMovie()/onPlayEpisode() — this screen just passes whatever
    // DetailsViewModel put on the event through unchanged.
    LaunchedEffect(viewModel) {
        viewModel.navigateToPlayer.collectLatest { navArgs ->
            onNavigateToPlayer(
                navArgs.mediaId,
                navArgs.episodeId,
                navArgs.resumeMs,
                navArgs.preferredSource
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(uiState.media?.title.orEmpty()) },
            navigationIcon = {
                Button(onClick = onNavigateBack) {
                    Text(stringResource(R.string.details_back))
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoadingMedia -> LoadingContent()

                uiState.mediaError != null -> ErrorContent(
                    error = uiState.mediaError!!,
                    onRetry = viewModel::retryMedia
                )

                uiState.media != null -> MediaContent(
                    media = uiState.media!!,
                    episodes = uiState.episodes,
                    isLoadingEpisodes = uiState.isLoadingEpisodes,
                    episodesError = uiState.episodesError,
                    onPlayMovie = viewModel::onPlayMovie,
                    onPlayEpisode = viewModel::onPlayEpisode,
                    onRetryEpisodes = viewModel::retryEpisodes,
                    onChooseStream = viewModel::onChooseStream
                )
            }
        }
    }

    val picker = uiState.picker
    if (picker != PickerUiState.Closed) {
        PickerSheet(
            picker = picker,
            onCandidateSelected = viewModel::onCandidateSelected,
            onRetry = viewModel::retryChooseStream,
            onDismiss = viewModel::onDismissPicker
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MediaContent(
    media: Media,
    episodes: List<Episode>,
    isLoadingEpisodes: Boolean,
    episodesError: AppError?,
    onPlayMovie: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onRetryEpisodes: () -> Unit,
    onChooseStream: (Episode?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MediaHeader(media = media)

        when (media.type) {
            MediaType.MOVIE -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onPlayMovie) {
                        Text(stringResource(R.string.details_play))
                    }
                    TextButton(
                        onClick = { onChooseStream(null) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.details_choose_stream))
                    }
                }
            }

            MediaType.TV_SHOW -> EpisodeList(
                episodes = episodes,
                isLoading = isLoadingEpisodes,
                error = episodesError,
                onEpisodeClick = onPlayEpisode,
                onChooseStream = onChooseStream,
                onRetry = onRetryEpisodes
            )
        }
    }
}

@Composable
private fun MediaHeader(media: Media) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = media.title,
            style = MaterialTheme.typography.headlineSmall
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
        media.overview?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Episode>,
    isLoading: Boolean,
    error: AppError?,
    onEpisodeClick: (Episode) -> Unit,
    onChooseStream: (Episode?) -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> LoadingContent()

        error != null -> ErrorContent(error = error, onRetry = onRetry)

        episodes.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.details_no_episodes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        else -> {
            // Grouped by season, sorted by season then episode number, so
            // the list reads in natural viewing order regardless of the
            // order MediaRepository.getEpisodes() returns them in.
            val grouped = episodes
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                .groupBy { it.seasonNumber }

            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                grouped.forEach { (seasonNumber, seasonEpisodes) ->
                    item {
                        Text(
                            text = stringResource(R.string.details_season_header, seasonNumber),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(seasonEpisodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = { onEpisodeClick(episode) },
                            onChooseStream = { onChooseStream(episode) }
                        )
                    }
                }
            }
        }
    }
}@Composable
private fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    onChooseStream: () -> Unit
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
                text = episode.title ?: stringResource(
                    R.string.details_episode_fallback_title,
                    episode.episodeNumber
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            episode.overview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        TextButton(onClick = onChooseStream) {
            Text(stringResource(R.string.details_choose_stream))
        }
    }
}

/**
 * The manual stream-candidate picker sheet (stream-candidate picker
 * feature). A ModalBottomSheet rather than a new Route/nav destination —
 * it's a transient, dismissible overlay on top of Details, not a place
 * the user navigates to and from independently (see Details' picker
 * design discussion — this stays lightweight rather than becoming its own
 * screen with its own back-stack entry).
 *
 * rememberModalBottomSheetState() is recreated implicitly whenever this
 * composable enters composition (i.e. whenever [picker] transitions away
 * from Closed) since there is no key tying it to a longer-lived scope —
 * this matches ModalBottomSheet's typical usage where the sheet's own
 * composable is only present while shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    picker: PickerUiState,
    onCandidateSelected: (StreamCandidate) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.details_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (picker) {
                PickerUiState.Closed -> Unit // Not rendered — see DetailsScreen's guard.

                is PickerUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is PickerUiState.Error -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.details_picker_error_generic),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (picker.error.isRecoverable) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.player_retry))
                        }
                    }
                }

                is PickerUiState.Loaded -> if (picker.candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.details_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        picker.candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                onClick = { onCandidateSelected(candidate) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single selectable row in the picker sheet.
 *
 * No stable key/LazyColumn here deliberately — candidate lists are search
 * results with no stable id of their own (StreamCandidate has no id field;
 * hash is nullable and not guaranteed unique across a single search's
 * results — see SearchResult.kt), and are expected to be short enough
 * (a single search's worth of results) that a plain Column is simpler and
 * sufficient rather than introducing an artificial key.
 */
@Composable
private fun CandidateRow(candidate: StreamCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2
            )
            val seedersText = candidate.seeders?.let {
                stringResource(R.string.details_picker_seeders, it)
            }
            val details = buildString {
                append(formatVideoQuality(candidate.quality))
                candidate.sizeBytes?.let {
                    append(" • ")
                    append(formatFileSize(it))
                }
                seedersText?.let {
                    append(" • ")
                    append(it)
                }
            }
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Plain, non-localized display strings for VideoQuality/file size.
 *
 * Not routed through stringResource() / strings.xml, unlike the rest of
 * this screen's copy — VideoQuality's cases (SD/HD_720/HD_1080/UHD_4K/
 * UNKNOWN) and byte-size formatting are both effectively technical/
 * numeric labels rather than user-facing prose that benefits from
 * translation, similar in spirit to how mediaId is shown directly on
 * HomeScreen's Continue Watching rows today. Flagging this as a
 * deliberate scope choice for this session rather than an oversight —
 * proper localization of these two specific labels can be revisited later
 * if it becomes a priority.
 */
private fun formatVideoQuality(quality: VideoQuality): String = when (quality) {
    VideoQuality.SD -> "SD"
    VideoQuality.HD_720 -> "720p"
    VideoQuality.HD_1080 -> "1080p"
    VideoQuality.UHD_4K -> "4K"
    VideoQuality.UNKNOWN -> "Unknown quality"
}

private fun formatFileSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    return "%.0f MB".format(mb)
}

/**
 * Error presentation. Reuses the same isRecoverable split as PlayerScreen's
 * ErrorContent / SearchScreen's ErrorContent — same convention, third copy
 * of it. See PlayerScreen.kt for the full reasoning.
 */
@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    val message = detailsErrorMessage(error)

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
 * User-facing copy for each AppError case relevant to Details. Kept local
 * to this screen, same convention as PlayerScreen/SearchScreen.
 */
@Composable
private fun detailsErrorMessage(error: AppError): String = when (error) {
    is AppError.NoNetworkConnection -> stringResource(R.string.player_error_no_network)
    is AppError.AllProvidersUnavailable -> stringResource(R.string.search_error_providers_unavailable)
    is AppError.NotAuthenticated -> stringResource(R.string.player_error_not_authenticated)
    is AppError.NoCachedStreamAvailable -> stringResource(R.string.player_error_generic)
    is AppError.StreamResolutionFailed -> stringResource(R.string.player_error_generic)
    is AppError.LocalStorageError -> stringResource(R.string.player_error_generic)
    is AppError.Unknown -> stringResource(R.string.player_error_generic)
}