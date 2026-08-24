package com.onedebrid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.ui.navigation.PlayerNavArgs
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetContinueWatchingUseCase
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
 * Session 27: resolvingMediaId and resumeError (Session 25) have been
 * removed. Tapping a Continue Watching row used to resolve a full Media
 * here before navigating (so a resolution failure could be shown inline on
 * the row without leaving Home) — as of Session 27, onItemClick() navigates
 * immediately via nav args, and PlayerViewModel resolves Media itself once
 * the Player screen is reached. Any resolution failure now surfaces there
 * instead, using PlayerScreen's existing error card + retry, rather than
 * inline on this screen. This was a deliberate, discussed tradeoff (see
 * currentsprint.md Session 27 notes), not an oversight — tapping a row now
 * always navigates instantly, and a failed resolution requires a back-press
 * to return to Home rather than staying on Home the whole time.
 */
data class HomeUiState(
    val continueWatching: List<WatchedItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val getContinueWatchingUseCase: GetContinueWatchingUseCase,
    private val removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // One-shot navigation event carrying the nav args Route.Player.build()
    // needs (Session 27 — previously Channel<Unit>, since PendingPlaybackHolder
    // carried the actual payload out of band). A Channel rather than a
    // second StateFlow so the navigation event fires exactly once and is
    // never accidentally replayed on recomposition or configuration change,
    // which a StateFlow's conflated-replay-of-1 semantics would risk.
    private val _navigateToPlayer = Channel<PlayerNavArgs>(Channel.BUFFERED)
    val navigateToPlayer: Flow<PlayerNavArgs> = _navigateToPlayer.receiveAsFlow()

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
     * Emit a navigation event for a Continue Watching row tap, carrying
     * exactly the nav args Route.Player.build() needs.
     *
     * Session 27: previously resolved a full Media here via
     * GetMediaByIdUseCase before navigating (see this class's old
     * resolvingMediaId/resumeError state, now removed). No longer does —
     * this method only reads fields already present on WatchedItem and
     * emits immediately; PlayerViewModel resolves Media/Episode itself
     * from the mediaId/episodeId nav args once Player is reached. This
     * also means HomeViewModel no longer needs GetMediaByIdUseCase or
     * PendingPlaybackHolder at all.
     *
     * No-op if no profile is active yet. Unlike the old version, there is
     * no "resolution already in flight" guard needed anymore — navigation
     * is now synchronous and instant, so there's nothing to race.
     */
    fun onItemClick(item: WatchedItem) {
        _activeProfile.value ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = item.mediaId,
                episodeId = item.episodeId,
                resumeMs = item.positionMs
            )
        )
    }
}