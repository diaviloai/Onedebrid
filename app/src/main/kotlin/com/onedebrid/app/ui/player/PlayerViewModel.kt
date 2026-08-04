package com.onedebrid.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * ViewModel for the Player screen.
 *
 * Naming note: PlaybackState exists as two distinct types in this codebase —
 * an enum in SessionState.kt describing the ExoPlayer lifecycle
 * (IDLE/BUFFERING/PLAYING/PAUSED/ENDED/ERROR), and a sealed interface in
 * PlaybackCoordinator.kt describing the resolve-and-start workflow
 * (Idle/Resolving/Ready/Error). This ViewModel is the first file that needs
 * both, so both are imported with aliases: CoordinatorState for the workflow
 * type, PlayerLifecycleState for the player lifecycle enum. Decided here
 * rather than renaming either source type, since neither name is wrong on
 * its own and a rename would ripple into files that already build cleanly.
 * If a third file ever needs both types, this alias pair should be reused
 * rather than reinvented.
 *
 * Scope of this ViewModel as written:
 * - Starts playback via PlaybackCoordinator.play(), given a PlaybackRequest
 *   and the active profile ID (the screen/nav layer is responsible for
 *   supplying both — this ViewModel does not look up the active profile
 *   itself, unlike HomeViewModel/SearchViewModel, because a Player screen
 *   is always entered with a specific request already in hand).
 * - Tracks CoordinatorState to know when resolution finished and a
 *   StreamSource is ready to hand to ExoPlayer, or whether it's still
 *   resolving or failed.
 * - Tracks PlayerLifecycleState as a plain field the eventual Compose
 *   player screen updates via onPlayerStateChanged(), driven by ExoPlayer's
 *   Player.Listener callbacks (onPlaybackStateChanged, onIsPlayingChanged).
 *   This ViewModel does not talk to ExoPlayer directly — that stays in the
 *   UI layer per the UI layer boundary rule in Technical_standards.md.
 *
 * Deliberately NOT included here (flagged rather than built silently):
 * - Persisting playback position. SessionRepository.updatePlaybackPosition()
 *   exists but no use case wraps it yet, so there's nothing for this
 *   ViewModel to call. Continue Watching position saving is a separate
 *   concern per the Session 12 note on RecordPlaybackUseCase, and needs its
 *   own use case (e.g. SavePlaybackPositionUseCase) before a Player
 *   ViewModel can persist position on pause/stop.
 * - Ending the session (SessionRepository.endPlaybackSession()) — same
 *   gap, no use case wraps it yet.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackCoordinator: PlaybackCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        playbackCoordinator.state
            .onEach { coordinatorState ->
                _uiState.value = _uiState.value.copy(coordinatorState = coordinatorState)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Begin resolving and starting playback for the given request.
     *
     * Delegates entirely to PlaybackCoordinator, which handles resolution,
     * session start, history recording, and cancellation of any prior
     * in-progress request.
     */
    fun play(request: PlaybackRequest, profileId: String) {
        playbackCoordinator.play(request, profileId)
    }

    /**
     * Stop the current playback workflow and reset coordinator state to Idle.
     */
    fun stop() {
        playbackCoordinator.stop()
    }

    /**
     * Called by the Compose player screen in response to ExoPlayer's
     * Player.Listener callbacks, so this ViewModel's state reflects what
     * the player is actually doing rather than only the resolve/start
     * workflow's outcome.
     */
    fun onPlayerStateChanged(newState: PlayerLifecycleState) {
        _uiState.value = _uiState.value.copy(playerLifecycleState = newState)
    }
}

/**
 * The complete rendering state for the Player screen.
 *
 * coordinatorState: Where the resolve-and-start workflow is
 * (Idle/Resolving/Ready/Error) — drives whether to show a loading spinner,
 * an error view, or hand the resolved StreamSource to ExoPlayer.
 *
 * playerLifecycleState: What ExoPlayer itself is doing once a stream is
 * loaded (IDLE/BUFFERING/PLAYING/PAUSED/ENDED/ERROR) — drives playback
 * controls (play/pause icon, buffering spinner over the video surface).
 *
 * These are independent because a Ready coordinator state doesn't mean
 * the player has started playing yet — there's a gap between "stream
 * resolved" and "ExoPlayer reports PLAYING" that the UI needs to show
 * buffering for.
 */
data class PlayerUiState(
    val coordinatorState: CoordinatorState = CoordinatorState.Idle,
    val playerLifecycleState: PlayerLifecycleState = PlayerLifecycleState.IDLE
)