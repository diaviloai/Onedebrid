package com.onedebrid.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.usecase.EndPlaybackSessionUseCase
import com.onedebrid.app.usecase.SavePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
 * Position saving and session ending (wired in Session 19):
 * - SavePlaybackPositionUseCase is called on a periodic ticker while the
 *   player reports PLAYING (every POSITION_SAVE_INTERVAL_MS), and once
 *   more immediately whenever the player reports PAUSED or ENDED. The
 *   ticker is cancelled whenever playback is not actively PLAYING, so it
 *   never runs while paused, buffering, idle, or errored.
 * - EndPlaybackSessionUseCase is called from stop() only. onCleared() only
 *   stops the position-save ticker — it cannot reliably run the suspend
 *   session-end call (see onCleared()'s doc comment below for why). This
 *   is a known gap when the screen is left without an explicit stop().
 * - This ViewModel still does not talk to ExoPlayer directly. It relies on
 *   the Compose player screen to report both the lifecycle state AND the
 *   current position via onPlayerStateChanged(), since only the screen
 *   holds the ExoPlayer instance that knows currentPosition.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackCoordinator: PlaybackCoordinator,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val endPlaybackSessionUseCase: EndPlaybackSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Tracks the last position reported by the player screen so the
    // periodic ticker has something to save without the screen needing to
    // call a separate "tick" method itself.
    private var lastKnownPositionMs: Long = 0L

    private var positionSaveJob: Job? = null

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
     * Stop the current playback workflow, end the in-memory session, and
     * reset coordinator state to Idle.
     *
     * Called when the user deliberately stops playback (as opposed to
     * onCleared(), which handles the case where the screen is simply
     * navigated away from without an explicit stop).
     */
    fun stop() {
        stopPositionSaving()
        playbackCoordinator.stop()
        viewModelScope.launch {
            endPlaybackSessionUseCase()
        }
    }

    /**
     * Called by the Compose player screen in response to ExoPlayer's
     * Player.Listener callbacks, so this ViewModel's state reflects what
     * the player is actually doing rather than only the resolve/start
     * workflow's outcome.
     *
     * positionMs is the player's currentPosition at the moment of the
     * callback. It is cached for the periodic ticker and, on PAUSED/ENDED,
     * saved immediately rather than waiting for the next tick.
     */
    fun onPlayerStateChanged(newState: PlayerLifecycleState, positionMs: Long) {
        lastKnownPositionMs = positionMs
        _uiState.value = _uiState.value.copy(playerLifecycleState = newState)

        when (newState) {
            PlayerLifecycleState.PLAYING -> startPositionSaving()
            PlayerLifecycleState.PAUSED, PlayerLifecycleState.ENDED -> {
                stopPositionSaving()
                viewModelScope.launch {
                    savePlaybackPositionUseCase(positionMs)
                }
            }
            else -> stopPositionSaving()
        }
    }

    /**
     * Starts a ticker that saves the last known position every
     * POSITION_SAVE_INTERVAL_MS while playback continues. No-ops if a
     * ticker is already running (PLAYING can be reported more than once
     * in a row by some Player.Listener implementations).
     */
    private fun startPositionSaving() {
        if (positionSaveJob?.isActive == true) return
        positionSaveJob = viewModelScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                savePlaybackPositionUseCase(lastKnownPositionMs)
            }
        }
    }

    private fun stopPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    /**
     * Stops the position-save ticker when the ViewModel is torn down (e.g.
     * the user navigates away without calling stop() explicitly, such as a
     * system back gesture).
     *
     * Deliberately does NOT call endPlaybackSessionUseCase() here. Android
     * cancels viewModelScope before onCleared() runs, and any coroutine
     * launched inside onCleared() is cancelled essentially immediately —
     * there is no reliable way to run suspend work at this point. This is a
     * real gap, not a solved problem: if the Player screen is left via back
     * gesture rather than an explicit stop(), the in-memory session can
     * still report a stale "playing" PlaybackSession. Closing this gap
     * properly means the navigation/UI layer calling stop() itself before
     * tearing down the screen (e.g. from DisposableEffect's onDispose, or
     * an explicit back handler) rather than relying on onCleared() to do
     * suspend work it structurally cannot do. That wiring belongs to the
     * Compose player screen, not this ViewModel, and is not built yet.
     */
    override fun onCleared() {
        stopPositionSaving()
        super.onCleared()
    }
}

private const val POSITION_SAVE_INTERVAL_MS = 5_000L

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