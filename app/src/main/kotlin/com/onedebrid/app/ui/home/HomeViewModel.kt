package com.onedebrid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.ui.navigation.PendingPlaybackHolder
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetContinueWatchingUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.RemoveFromContinueWatchingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Home / Continue Watching screen.
 *
 * Not modeled as a sealed Loading/Ready state because there is only one
 * Flow feeding this screen (no combine() needed, unlike ProfileViewModel),
 * and an empty list is a legitimate, renderable state rather than a
 * loading state. [isLoading] only reflects the brief window before the
 * first emission from Continue Watching arrives for the active profile.
 *
 * [resolvingMediaId] (Session 25) is the mediaId currently being resolved
 * to a full Media for tap-to-resume, or null if no row is resolving. Only
 * one row can resolve at a time — a second tap while one is in flight is
 * ignored (see onItemClick) rather than queued, since starting a second
 * concurrent resolution has no clear UX benefit and complicates state.
 *
 * [resumeError] (Session 25) is the AppError from the most recent failed
 * resolution, or null. Cleared on the next tap attempt. Expected to be
 * AppError.AllProvidersUnavailable in practice today, since
 * MetadataProvider is still StubMetadataProvider — see GetMediaByIdUseCase
 * and MediaRepositoryImpl's doc comments. This is surfaced visibly (see
 * HomeScreen) rather than silently swallowed, matching the project's
 * "flag rather than silently work around" convention.
 */
data class HomeUiState(
    val continueWatching: List<WatchedItem> = emptyList(),
    val isLoading: Boolean = true,
    val resolvingMediaId: String? = null,
    val resumeError: AppError? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val getContinueWatchingUseCase: GetContinueWatchingUseCase,
    private val removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val pendingPlaybackHolder: PendingPlaybackHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // One-shot navigation event (Session 25) — this screen is the first to
    // need async work (a suspend Media lookup) between a user tap and
    // navigation, unlike SearchScreen where the full Media is already in
    // hand synchronously at tap time (see SearchScreen.kt's doc comment).
    // A Channel is used rather than a second StateFlow so the navigation
    // event fires exactly once and is never accidentally replayed on
    // recomposition or configuration change, which a StateFlow's
    // conflated-replay-of-1 semantics would risk.
    private val _navigateToPlayer = Channel<Unit>(Channel.BUFFERED)
    val navigateToPlayer: Flow<Unit> = _navigateToPlayer.receiveAsFlow()

    // Mirrors SearchViewModel's pattern: the active profile is tracked
    // privately so removeItem() can read profileId synchronously without
    // re-collecting the active profile Flow on every call.
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)

    init {
        getActiveProfileUseCase()
            .onEach { profile ->
                _activeProfile.value = profile
                observeContinueWatching(profile.id)
            }
            .launchIn(viewModelScope)
    }

    private var continueWatchingJob: kotlinx.coroutines.Job? = null

    private fun observeContinueWatching(profileId: String) {
        // Re-scope observation whenever the active profile changes, same
        // as SearchViewModel re-scopes search history on profile switch.
        continueWatchingJob?.cancel()
        continueWatchingJob = getContinueWatchingUseCase(profileId)
            .onEach { items ->
                _uiState.value = _uiState.value.copy(
                    continueWatching = items,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Remove a single item from Continue Watching.
     *
     * No-op if no profile is active yet, matching the guard pattern used
     * by SearchViewModel's search()/clearHistory().
     *
     * Fire-and-forget: PlaybackRepository.removeFromContinueWatching()
     * returns Unit, not a RepositoryResult, so there is no structured
     * failure to surface here. A failed removal currently fails silently.
     */
    fun removeItem(mediaId: String) {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            removeFromContinueWatchingUseCase(
                profileId = profileId,
                mediaId = mediaId
            )
        }
    }

    /**
     * Resolve a Continue Watching row to a full Media and, on success,
     * populate PendingPlaybackHolder and emit a navigation event for
     * HomeScreen/NavGraph to act on (Session 25).
     *
     * No-op if no profile is active yet, or if a resolution is already in
     * flight (see resolvingMediaId doc comment above) — a second tap on
     * the same or a different row while one is resolving is ignored rather
     * than queued or cancelling the first.
     *
     * Builds episode/resumePositionMs directly from the WatchedItem's own
     * fields rather than re-deriving them, since WatchedItem already
     * carries exactly this context (see its doc comment). For a TV
     * episode, only seasonNumber/episodeNumber/episodeId are known here —
     * not a full Episode object (title, overview, etc.), since WatchedItem
     * doesn't carry one. PlaybackRequest.episode is therefore built as a
     * minimal Episode using just those three fields; PlayerScreen/
     * PlayerViewModel already tolerate an Episode with only its required
     * fields populated (seasonNumber/episodeNumber/id/mediaId), same as
     * any other partially-enriched Episode per that model's own doc
     * comment ("can be created from minimal information").
     */
    fun onItemClick(item: WatchedItem) {
        val profileId = _activeProfile.value?.id ?: return
        if (_uiState.value.resolvingMediaId != null) return

        _uiState.value = _uiState.value.copy(
            resolvingMediaId = item.mediaId,
            resumeError = null
        )

        viewModelScope.launch {
            when (val result = getMediaByIdUseCase(item.mediaId)) {
                is RepositoryResult.Success -> {
                    val episode = if (item.episodeId != null &&
                        item.seasonNumber != null &&
                        item.episodeNumber != null
                    ) {
                        Episode(
                            id = item.episodeId,
                            mediaId = item.mediaId,
                            seasonNumber = item.seasonNumber,
                            episodeNumber = item.episodeNumber
                        )
                    } else {
                        null
                    }
                    val request = PlaybackRequest(
                        media = result.data,
                        episode = episode,
                        preferredSource = null,
                        resumePositionMs = item.positionMs
                    )
                    pendingPlaybackHolder.set(request, profileId)
                    _uiState.value = _uiState.value.copy(resolvingMediaId = null)
                    _navigateToPlayer.trySend(Unit)
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        resolvingMediaId = null,
                        resumeError = result.error
                    )
                }
            }
        }
    }
}