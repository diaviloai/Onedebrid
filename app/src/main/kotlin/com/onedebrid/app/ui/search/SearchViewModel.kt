package com.onedebrid.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.coordinator.SearchCoordinator
import com.onedebrid.app.coordinator.SearchState
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.usecase.ClearSearchHistoryUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetSearchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the search screen.
 *
 * Coordinates between the SearchCoordinator (which owns execution) and
 * GetSearchHistoryUseCase (which owns history). Exposes a single UiState
 * that carries both independently, because they serve different UI purposes:
 * history is always visible, search state is transient.
 *
 * The active profile is observed to scope history and to supply a profileId
 * to the coordinator. If no profile is active yet, search and history
 * operations are no-ops.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCoordinator: SearchCoordinator,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val getActiveProfileUseCase: GetActiveProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Active profile — collected separately so we can read its ID
    // synchronously when the user triggers a search or clear.
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)

    init {
        // Track the active profile
        getActiveProfileUseCase()
            .onEach { profile ->
                _activeProfile.value = profile
                // Mirror the active profile's id into UiState. As of
                // Session 26 this is no longer read by SearchScreen itself
                // (tapping a result navigates to Details instead of playing
                // directly, and Details/Player resolve the active profile
                // themselves via PlayerNavArgs + their own ViewModels) —
                // left in place per Dia's Session 26 call rather than
                // removed, flagged in currentsprint.md's Open TODOs to
                // revisit near project end if still unused.
                _uiState.value = _uiState.value.copy(activeProfileId = profile.id)
                // When the profile changes, start observing its history.
                // History observation is started here rather than once at init
                // so it naturally re-scopes if the profile switches.
                observeHistory(profile.id)
            }
            .launchIn(viewModelScope)

        // Track coordinator state — this drives the search result / loading UI
        searchCoordinator.state
            .onEach { coordinatorState ->
                _uiState.value = _uiState.value.copy(
                    searchState = coordinatorState
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Submit a search query.
     *
     * Delegates to the SearchCoordinator, which handles execution,
     * history persistence, and cancellation of prior searches.
     * No-op if no profile is active yet.
     */
    fun search(query: String) {
        val profileId = _activeProfile.value?.id ?: return
        searchCoordinator.search(query, profileId)
    }

    /**
     * Clear the active search and return to Idle.
     *
     * Called when the user clears the search bar or navigates away.
     */
    fun clearSearch() {
        searchCoordinator.clear()
    }

    /**
     * Clear all search history for the active profile.
     *
     * Fire-and-forget — history updates will arrive automatically
     * through the observed Flow.
     */
    fun clearHistory() {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            clearSearchHistoryUseCase(profileId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun observeHistory(profileId: String) {
        getSearchHistoryUseCase(profileId)
            .onEach { history ->
                _uiState.value = _uiState.value.copy(searchHistory = history)
            }
            .launchIn(viewModelScope)
    }
}

// ── UI State ──────────────────────────────────────────────────────────────────

/**
 * The complete rendering state for the search screen.
 *
 * Unlike ProfileUiState, this is a data class rather than a sealed interface
 * because both fields coexist — history is always visible alongside whatever
 * state the search execution is in.
 *
 * searchState: Current execution state (idle / searching / results / error).
 * searchHistory: Past queries for the active profile, shown when idle.
 * activeProfileId: The currently active profile's id, or null if no profile
 * is active yet. Unused by SearchScreen as of Session 26 (see the init{}
 * block's comment) — left in place, flagged in currentsprint.md to revisit
 * near project end if still unused.
 */
data class SearchUiState(
    val searchState: SearchState = SearchState.Idle,
    val searchHistory: List<String> = emptyList(),
    val activeProfileId: String? = null
)