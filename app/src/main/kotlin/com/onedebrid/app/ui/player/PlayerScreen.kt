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
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState

/**
 * The Player screen.
 *
 * This is the file responsible for closing the Session 19 gap documented in
 * PlayerViewModel: PlayerViewModel.onCleared() cannot reliably run the
 * suspend call inside EndPlaybackSessionUseCase, because Android cancels
 * viewModelScope before onCleared() runs. The fix belongs here, not in the
 * ViewModel — see the DisposableEffect below.
 *
 * ExoPlayer ownership:
 * PlayerViewModel never touches ExoPlayer directly (UI layer boundary rule,
 * Technical_standards.md). This screen creates and owns the ExoPlayer
 * instance, feeds it the resolved StreamSource once CoordinatorState becomes
 * Ready, and reports every lifecycle change back to the ViewModel via
 * onPlayerStateChanged(newState, positionMs, durationMs) — the ViewModel
 * has no other way to know what the player is actually doing or where it
 * currently is.
 *
 * Session 27 — no more PendingPlaybackHolder:
 * This screen used to take pendingPlaybackHolder and onMissingRequest as
 * parameters, reading a pre-built PlaybackRequest out of the holder on
 * first composition. As of Session 27, PlayerViewModel resolves entirely
 * from nav arguments (via SavedStateHandle) on its own — this screen no
 * longer takes any parameters beyond the standard modifier/viewModel, and
 * there is no onMissingRequest case anymore, since a mediaId is always
 * present as a required path segment. See PlayerViewModel.kt's doc comment
 * for the full resolve flow this screen now waits on.
 *
 * Two-phase state:
 * uiState.resolveState (Resolving/Resolved/Error) covers the new Session 27
 * resolve phase — looking up Media/Episode/active profile from nav args.
 * uiState.coordinatorState (Idle/Resolving/Ready/Error) covers the
 * pre-existing resolve-and-start-playback phase, which only becomes
 * meaningful once resolveState is Resolved. This screen renders
 * resolveState first: while it's Resolving or Error, coordinatorState is
 * not yet meaningful (PlaybackCoordinator.play() hasn't been called yet)
 * and is ignored.
 */
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ExoPlayer is created once per screen instance and lives for as long as
    // this composable is in the composition. `remember` (not
    // rememberSaveable — ExoPlayer isn't parcelable) ties its lifetime to
    // composition, matching the DisposableEffect below that releases it.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // Releases ExoPlayer and stops the playback session on teardown,
    // regardless of which phase (resolve or coordinator) the screen was in
    // when the user navigated away. This is the actual fix for the Session
    // 19 gap — onDispose fires reliably on back gesture, forward
    // navigation, or any other teardown of this screen — unlike
    // onCleared(), it runs while viewModelScope is still alive, so stop()'s
    // suspend call to EndPlaybackSessionUseCase actually completes instead
    // of being cancelled mid-flight.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
            exoPlayer.release()
        }
    }

    // Feed the resolved stream to ExoPlayer as soon as the coordinator
    // reports Ready. Re-keying on the source id (not the whole state)
    // avoids reloading the same media if this recomposes for unrelated
    // reasons (e.g. playerLifecycleState changing).
    val coordinatorState = uiState.coordinatorState
    if (coordinatorState is CoordinatorState.Ready) {
        DisposableEffect(coordinatorState.source.id) {
            val mediaItem = MediaItem.fromUri(coordinatorState.source.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            onDispose { }
        }
    }

    // Mirror ExoPlayer's own lifecycle into PlayerViewModel so the rest of
    // the app (Continue Watching, session end) reflects what is actually
    // happening in the player, not just the resolve/start outcome.
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val mapped = when (playbackState) {
                    Player.STATE_IDLE -> PlayerLifecycleState.IDLE
                    Player.STATE_BUFFERING -> PlayerLifecycleState.BUFFERING
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying) PlayerLifecycleState.PLAYING
                        else PlayerLifecycleState.PAUSED
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

                    is CoordinatorState.Ready -> PlayerSurface(exoPlayer = exoPlayer)

                    is CoordinatorState.Error -> ErrorContent(
                        error = coordinatorState.error,
                        onRetry = { viewModel.retryPlay() }
                    )
                }
            }
        }
    }
}

/**
 * The actual video surface, shown once CoordinatorState is Ready.
 *
 * PlayerView is a classic Android View (Media3 has no Compose-native
 * player surface as of media3 1.6.1), so it is embedded via AndroidView
 * per the standard Compose interop pattern.
 */
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

/**
 * Error presentation, split by AppError.isRecoverable per the severity
 * tiers defined in UI_UX_Design.md:
 *
 * - isRecoverable == true  → Recoverable Operational tier: inline card with
 *   a Retry action (e.g. StreamResolutionFailed, NoNetworkConnection,
 *   AllProvidersUnavailable — the kind of failure where trying again is a
 *   reasonable next step).
 * - isRecoverable == false → Critical/Fatal tier: full-screen state with no
 *   retry offered (e.g. NoCachedStreamAvailable, NotAuthenticated — trying
 *   the exact same request again would fail the same way).
 *
 * As of Session 27, this is shared between resolveState's Error and
 * coordinatorState's Error — both wrap an AppError and both fit these same
 * tiers, so a single composable and error-message mapping serve both call
 * sites (see PlayerScreen's when block above).
 *
 * The UI/UX doc's third tier, Non-blocking/Background (Snackbar), is
 * deliberately not used on this screen. That tier is for failures that
 * don't block the rest of the screen, like metadata enrichment failing
 * while a title still shows. Every AppError this screen can receive blocks
 * the screen's entire purpose — there's nothing else here to show while a
 * Snackbar's message fades. Flagging this explicitly rather than forcing a
 * tier that doesn't fit the content.
 */
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

/**
 * User-facing copy for each AppError case relevant to playback.
 *
 * Kept local to this screen rather than added to AppError itself —
 * AppError is a domain type and should not own presentation strings
 * (Technical_standards.md: DTOs/domain types stay presentation-agnostic).
 *
 * AppError.Unknown's generic message now also covers the Session 27
 * "episode not found" case from GetEpisodeByIdUseCase/MediaRepositoryImpl —
 * see that method's doc comment. No dedicated string was added for it,
 * consistent with Unknown already being a catch-all case here.
 */
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