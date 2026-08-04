package com.onedebrid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetContinueWatchingUseCase
import com.onedebrid.app.usecase.RemoveFromContinueWatchingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
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

    // Mirrors SearchViewModel's pattern: the active profile is tracked
    // privately so removeItem() can read profileId synchronously without
    // re-collecting the active profile Flow on every call.
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)

    init {
        viewModelScope.launch {
            getActiveProfileUseCase()
                .onEach { profile ->
                    _activeProfile.value = profile
                    observeContinueWatching(profile.id)
                }
                .collect()
        }
    }

    private var continueWatchingJob: kotlinx.coroutines.Job? = null

    private fun observeContinueWatching(profileId: String) {
        // Re-scope observation whenever the active profile changes, same
        // as SearchViewModel re-scopes search history on profile switch.
        continueWatchingJob?.cancel()
        continueWatchingJob = viewModelScope.launch {
            getContinueWatchingUseCase(profileId)
                .onEach { items ->
                    _uiState.value = _uiState.value.copy(
                        continueWatching = items,
                        isLoading = false
                    )
                }
                .collect()
        }
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
}