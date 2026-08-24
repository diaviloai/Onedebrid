package com.onedebrid.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.usecase.EndPlaybackSessionUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetEpisodeByIdUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.SavePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * (Idle/Resolving/Ready/Error). Both are imported with aliases:
 * CoordinatorState for the workflow type, PlayerLifecycleState for the
 * player lifecycle enum. This alias pair originates here and is reused
 * wherever else both types are needed.
 *
 * Session 27 — replaced PendingPlaybackHolder:
 * This ViewModel used to receive a ready-made PlaybackRequest (built by
 * whichever screen navigated here) via PendingPlaybackHolder, an in-memory
 * singleton that did not survive process death. As of Session 27, Player's
 * nav route (Route.Player in NavGraph.kt) carries only primitives —
 * mediaId (required), episodeId (optional), resumeMs (optional) — and this
 * ViewModel resolves everything else itself:
 * - Media via GetMediaByIdUseCase(mediaId)
 * - Episode via GetEpisodeByIdUseCase(mediaId, episodeId), only if an
 *   episodeId was actually passed
 * - The active profile via GetActiveProfileUseCase(), same pattern
 *   DetailsViewModel/HomeViewModel already use
 * preferredSource is NOT resolvable this way — nothing in the app produces
 * a pre-selected StreamCandidate yet (that's the stream-candidate picker
 * UI, a separate not-yet-built feature). PlaybackRequest.preferredSource
 * is always null here, same as it already was for every existing caller
 * (HomeViewModel, DetailsViewModel) before this change — this is not a
 * regression, just an explicitly acknowledged gap that was already true.
 *
 * This resolve step happens once, in resolveAndPlay(), called from
 * init{}. It is a NEW phase that did not exist before Session 27 — there
 * is now a window where this ViewModel is resolving Media/Episode/profile
 * before PlaybackCoordinator.play() can even be called. See ResolveState
 * below for how this is tracked and exposed to the screen.
 *
 * Scope otherwise unchanged from Session 19-26:
 * - Delegates actual stream resolution and session start to
 *   PlaybackCoordinator once a PlaybackRequest and profileId are in hand.
 * - Tracks CoordinatorState (Idle/Resolving/Ready/Error) for the
 *   resolve-and-start workflow.
 * - Tracks PlayerLifecycleState as reported by the Compose screen via
 *   onPlayerStateChanged(), driven by ExoPlayer's Player.Listener. This
 *   ViewModel never touches ExoPlayer directly (UI layer boundary rule,
 *   Technical_standards.md).
 * - Position saving / session ending: unchanged from Session 19, see
 *   individual method doc comments below.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackCoordinator: PlaybackCoordinator,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getEpisodeByIdUseCase: GetEpisodeByIdUseCase,
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val endPlaybackSessionUseCase: EndPlaybackSessionUseCase
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle["mediaId"]) {
        "PlayerScreen requires a mediaId nav argument"
    }

    // "none" / -1L are the sentinel values Route.Player.build() encodes
    // absence as, since NavType.StringType/LongType nav args have no
    // nullable variant that round-trips cleanly through SavedStateHandle
    // via the simple navArgument {} builder used in NavGraph.kt. Mapped
    // back to null here, immediately, so the rest of this class never
    // needs to know the sentinels exist.
    private val episodeId: String? =
        (savedStateHandle["episodeId"] as? String)?.takeIf { it != "none" }
    private val resumePositionMs: Long? =
        (savedStateHandle["resumeMs"] as? Long)?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Tracks the last position and duration reported by the player screen so
    // the periodic ticker has something to save without the screen needing
    // to call a separate "tick" method itself. durationMs is cached the same
    // way as positionMs even though it doesn't change during a single
    // playback session — ExoPlayer.duration is the only source of it (no
    // domain model carries media duration), and only the screen holds the
    // ExoPlayer instance, so it must be reported the same way position is.
    private var lastKnownPositionMs: Long = 0L
    private var lastKnownDurationMs: Long = 0L

    private var positionSaveJob: Job? = null

    // Kept around so retryResolve() (the resolve-phase equivalent of the
    // screen's own onRetry for CoordinatorState.Error) can re-run the exact
    // same resolve-and-play flow without duplicating resolveAndPlay()'s body.
    private var activeProfileId: String? = null

    init {
        playbackCoordinator.state
            .onEach { coordinatorState ->
                _uiState.value = _uiState.value.copy(coordinatorState = coordinatorState)
            }
            .launchIn(viewModelScope)

        resolveAndPlay()
    }

    /**
     * Resolves Media, Episode (if an episodeId was passed), and the active
     * profile, then builds a PlaybackRequest and hands it to
     * PlaybackCoordinator.play(). This is the Session 27 replacement for
     * reading a ready-made PlaybackRequest out of PendingPlaybackHolder —
     * see this class's own doc comment for the full reasoning.
     *
     * GetActiveProfileUseCase returns a Flow (there is always an active
     * profile once app state has settled, same assumption
     * DetailsViewModel/HomeViewModel already make) — .first() is used
     * rather than .onEach{}.launchIn() here because this ViewModel only
     * needs the profile once, at resolve time, not as an ongoing
     * subscription; unlike HomeViewModel/DetailsViewModel, nothing else in
     * this ViewModel needs to react to a live profile switch mid-playback.
     *
     * Resolve failures (Media or Episode lookup failing — expected to be
     * AppError.AllProvidersUnavailable in practice today, since
     * MetadataProvider is still StubMetadataProvider, same caveat as
     * GetMediaByIdUseCase's own doc comment) are surfaced via
     * ResolveState.Error on PlayerUiState, which PlayerScreen renders using
     * its existing ErrorContent composable — the same error presentation
     * already used for CoordinatorState.Error, just reused for a different
     * failure point. See PlayerScreen.kt for how the two are told apart.
     */
    private fun resolveAndPlay() {
        _uiState.value = _uiState.value.copy(resolveState = ResolveState.Resolving)

        viewModelScope.launch {
            val profile = getActiveProfileUseCase().first()
            activeProfileId = profile.id

            val mediaResult = getMediaByIdUseCase(mediaId)
            val media = when (mediaResult) {
                is RepositoryResult.Success -> mediaResult.data
                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        resolveState = ResolveState.Error(mediaResult.error)
                    )
                    return@launch
                }
            }

            val episode: Episode? = if (episodeId != null) {
                when (val episodeResult = getEpisodeByIdUseCase(mediaId, episodeId)) {
                    is RepositoryResult.Success -> episodeResult.data
                    is RepositoryResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            resolveState = ResolveState.Error(episodeResult.error)
                        )
                        return@launch
                    }
                }
            } else {
                null
            }

            _uiState.value = _uiState.value.copy(resolveState = ResolveState.Resolved)

            val request = PlaybackRequest(
                media = media,
                episode = episode,
                preferredSource = null,
                resumePositionMs = resumePositionMs
            )
            playbackCoordinator.play(request, profile.id)
        }
    }

    /**
     * Retry after a resolve-phase failure (ResolveState.Error) — re-runs
     * the entire resolve-and-play flow from scratch, same as retryMedia()
     * on DetailsViewModel. Distinct from a CoordinatorState.Error retry
     * (see PlayerScreen's onRetry, which re-plays a PlaybackRequest that
     * already resolved successfully) — this retries the earlier phase,
     * where Media/Episode/profile resolution itself failed before a
     * PlaybackRequest could even be built.
     */
    fun retryResolve() {
        resolveAndPlay()
    }

    /**
     * Retry after a CoordinatorState.Error — re-plays with the same,
     * already-resolved Media/Episode/profile rather than re-resolving them.
     * Called by PlayerScreen's ErrorContent when coordinatorState is
     * Error and resolveState is Resolved (see PlayerScreen.kt).
     */
    fun retryPlay() {
        val profileId = activeProfileId ?: return
        viewModelScope.launch {
            val mediaResult = getMediaByIdUseCase(mediaId)
            val media = (mediaResult as? RepositoryResult.Success)?.data ?: return@launch
            val episode = episodeId?.let {
                (getEpisodeByIdUseCase(mediaId, it) as? RepositoryResult.Success)?.data
            }
            val request = PlaybackRequest(
                media = media,
                episode = episode,
                preferredSource = null,
                resumePositionMs = resumePositionMs
            )
            playbackCoordinator.play(request, profileId)
        }
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
     *
     * durationMs is the player's duration at the moment of the callback.
     * ExoPlayer can report C.TIME_UNSET (-9223372036854775807L) before a
     * stream has loaded enough to know its duration — the screen is
     * responsible for passing whatever ExoPlayer.duration currently
     * returns, and this ViewModel caches it as-is. SavePlaybackPositionUseCase
     * and the underlying Room write both treat this as an opaque Long, so an
     * occasional C.TIME_UNSET value in early playback is not corrected here;
     * it would only affect a completion-percentage calculation, which is not
     * yet implemented as of Session 24 (see PlaybackRepository.saveProgress
     * doc comment — durationMs is currently stored but not yet consumed for
     * completion percentage or markAsCompleted thresholding).
     */
    fun onPlayerStateChanged(newState: PlayerLifecycleState, positionMs: Long, durationMs: Long) {
        lastKnownPositionMs = positionMs
        lastKnownDurationMs = durationMs
        _uiState.value = _uiState.value.copy(playerLifecycleState = newState)

        when (newState) {
            PlayerLifecycleState.PLAYING -> startPositionSaving()
            PlayerLifecycleState.PAUSED, PlayerLifecycleState.ENDED -> {
                stopPositionSaving()
                viewModelScope.launch {
                    savePlaybackPositionUseCase(positionMs, durationMs)
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
                savePlaybackPositionUseCase(lastKnownPositionMs, lastKnownDurationMs)
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
     * suspend work it structurally cannot do. This wiring exists in
     * PlayerScreen's DisposableEffect.onDispose (see that file).
     */
    override fun onCleared() {
        stopPositionSaving()
        super.onCleared()
    }
}

private const val POSITION_SAVE_INTERVAL_MS = 5_000L

/**
 * Tracks the Session 27 resolve phase — Media/Episode/active-profile
 * lookup that now happens inside this ViewModel before a PlaybackRequest
 * can be built, distinct from CoordinatorState (which only exists once a
 * PlaybackRequest is already in hand). See resolveAndPlay()'s doc comment.
 */
sealed interface ResolveState {
    data object Resolving : ResolveState
    data object Resolved : ResolveState
    data class Error(val error: AppError) : ResolveState
}

/**
 * The complete rendering state for the Player screen.
 *
 * resolveState: Session 27 addition. Where the Media/Episode/profile
 * resolve phase is. PlayerScreen shows a resolving/error state here before
 * coordinatorState becomes relevant at all — see PlayerScreen.kt for how
 * the two states are composed together.
 *
 * coordinatorState: Where the resolve-and-start workflow is
 * (Idle/Resolving/Ready/Error) — drives whether to show a loading spinner,
 * an error view, or hand the resolved StreamSource to ExoPlayer. Only
 * meaningful once resolveState is Resolved.
 *
 * playerLifecycleState: What ExoPlayer itself is doing once a stream is
 * loaded (IDLE/BUFFERING/PLAYING/PAUSED/ENDED/ERROR) — drives playback
 * controls (play/pause icon, buffering spinner over the video surface).
 */
data class PlayerUiState(
    val resolveState: ResolveState = ResolveState.Resolving,
    val coordinatorState: CoordinatorState = CoordinatorState.Idle,
    val playerLifecycleState: PlayerLifecycleState = PlayerLifecycleState.IDLE
)