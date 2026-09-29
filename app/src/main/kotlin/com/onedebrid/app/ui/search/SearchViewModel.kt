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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCoordinator: SearchCoordinator,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val getActiveProfileUseCase: GetActiveProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active profile — collected separately so we can read its ID
    // synchronously when the user triggers a search or clear.
    private val _activeProfile = MutableStateFlow<UserProfile?>(null)

    init {
        // Track the active profile
        getActiveProfileUseCase()
            .onEach { profile ->
                _activeProfile.value = profile
                _uiState.value = _uiState.value.copy(activeProfileId = profile.id)
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

        // Automatic search query debounce flow
        _searchQuery
            .debounce(500L)
            .distinctUntilChanged()
            .onEach { query ->
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    search(trimmed)
                } else if (query.isEmpty() && _uiState.value.searchState !is SearchState.Idle) {
                    clearSearch()
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Update the active search query text field.
     */
    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    /**
     * Submit a search query immediately (e.g. from history click or IME search).
     *
     * Delegates to the SearchCoordinator, which handles execution,
     * history persistence, and cancellation of prior searches.
     * No-op if no profile is active yet.
     */
    fun search(query: String) {
        _searchQuery.value = query
        val profileId = _activeProfile.value?.id ?: return
        searchCoordinator.search(query, profileId)
    }

    /**
     * Clear the active search query and return to Idle.
     *
     * Called when the user clears the search bar or navigates away.
     */
    fun clearSearch() {
        _searchQuery.value = ""
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
 */
data class SearchUiState(
    val searchState: SearchState = SearchState.Idle,
    val searchHistory: List<String> = emptyList(),
    val activeProfileId: String? = null
)
