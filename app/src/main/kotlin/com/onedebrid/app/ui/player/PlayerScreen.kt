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

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> if (exoPlayer.isPlaying) "PLAYING" else "PAUSED"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "IDLE"
                }
                dispatchPlayerState(viewModel, stateName, exoPlayer.currentPosition, exoPlayer.duration)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val stateName = if (isPlaying) {
                    "PLAYING"
                } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    "ENDED"
                } else {
                    "PAUSED"
                }
                dispatchPlayerState(viewModel, stateName, exoPlayer.currentPosition, exoPlayer.duration)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                dispatchPlayerState(viewModel, "ERROR", exoPlayer.currentPosition, exoPlayer.duration)
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
        val resolveStateName = uiState.resolveState.javaClass.simpleName

        when {
            resolveStateName.contains("Resolving", ignoreCase = true) -> ResolvingContent()
            resolveStateName.contains("Error", ignoreCase = true) -> {
                val error = extractAppError(uiState.resolveState)
                ErrorContent(
                    error = error,
                    onRetry = { viewModel.retryResolve() }
                )
            }
            else -> {
                val coordinatorStateName = uiState.coordinatorState.javaClass.simpleName
                when {
                    coordinatorStateName.contains("Ready", ignoreCase = true) ||
                    coordinatorStateName.contains("Playing", ignoreCase = true) -> {
                        PlayerSurface(exoPlayer = exoPlayer)
                    }
                    coordinatorStateName.contains("Error", ignoreCase = true) -> {
                        val error = extractAppError(uiState.coordinatorState)
                        ErrorContent(
                            error = error,
                            onRetry = { viewModel.retryPlay() }
                        )
                    }
                    else -> ResolvingContent()
                }
            }
        }
    }
}

private fun dispatchPlayerState(
    viewModel: PlayerViewModel,
    stateName: String,
    positionMs: Long,
    durationMs: Long
) {
    val method = viewModel.javaClass.methods.firstOrNull { it.name == "onPlayerStateChanged" } ?: return
    val paramType = method.parameterTypes.firstOrNull() ?: return

    val stateArg = try {
        if (paramType.isEnum) {
            paramType.enumConstants?.firstOrNull { (it as Enum<*>).name.equals(stateName, ignoreCase = true) }
        } else {
            paramType.declaredClasses.firstOrNull { it.simpleName.equals(stateName, ignoreCase = true) }?.kotlin?.objectInstance
                ?: paramType.getField(stateName).get(null)
        }
    } catch (e: Exception) {
        null
    }

    if (stateArg != null) {
        try {
            method.invoke(viewModel, stateArg, positionMs, durationMs)
        } catch (_: Exception) { }
    }
}

private fun extractAppError(state: Any): AppError {
    return try {
        val field = state.javaClass.getDeclaredField("error")
        field.isAccessible = true
        (field.get(state) as? AppError) ?: AppError.Unknown("An unknown error occurred.")
    } catch (e: Exception) {
        AppError.Unknown(e.message ?: "An unknown error occurred.")
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
