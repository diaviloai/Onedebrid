package com.onedebrid.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.onedebrid.app.R
import com.onedebrid.app.domain.error.AppError

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
            exoPlayer.release()
        }
    }

    val coordinatorState = uiState.coordinatorState
    if (coordinatorState is CoordinatorState.Playing) {
        DisposableEffect(coordinatorState.stream.id) {
            val mediaItem = MediaItem.fromUri(coordinatorState.stream.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            onDispose { }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val mapped = when (playbackState) {
                    Player.STATE_IDLE -> PlayerLifecycleState.IDLE
                    Player.STATE_BUFFERING -> PlayerLifecycleState.BUFFERING
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying) PlayerLifecycleState.PLAYING else PlayerLifecycleState.PAUSED
                    }
                    Player.STATE_ENDED -> PlayerLifecycleState.ENDED
                    else -> PlayerLifecycleState.IDLE
                }
                viewModel.onPlayerStateChanged(mapped, exoPlayer.currentPosition, exoPlayer.duration)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val mapped = if (isPlaying) {
                    PlayerLifecycleState.PLAYING
                } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    PlayerLifecycleState.ENDED
                } else {
                    PlayerLifecycleState.PAUSED
                }
                viewModel.onPlayerStateChanged(mapped, exoPlayer.currentPosition, exoPlayer.duration)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.onPlayerStateChanged(PlayerLifecycleState.ERROR, exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (val resolveState = uiState.resolveState) {
            is ResolveState.Resolving -> ResolvingContent()

            is ResolveState.Error -> ErrorContent(
                error = resolveState.error,
                onRetry = { viewModel.retryResolve() }
            )

            is ResolveState.Resolved -> {
                when (coordinatorState) {
                    is CoordinatorState.Idle,
                    is CoordinatorState.Resolving -> ResolvingContent()

                    is CoordinatorState.Playing -> PlayerSurface(exoPlayer = exoPlayer)

                    is CoordinatorState.Error -> ErrorContent(
                        error = coordinatorState.error,
                        onRetry = { viewModel.retryPlay() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSurface(exoPlayer: ExoPlayer) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
            }
        }
    )
}

@Composable
private fun ResolvingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.player_resolving),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    val message = errorMessage(error)

    if (error.isRecoverable) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.player_retry))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
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
