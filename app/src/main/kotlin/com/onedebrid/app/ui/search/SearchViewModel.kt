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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val _activeProfile = MutableStateFlow<UserProfile?>(null)

    init {
        getActiveProfileUseCase()
            .onEach { profile ->
                _activeProfile.value = profile
                _uiState.value = _uiState.value.copy(activeProfileId = profile.id)
                observeHistory(profile.id)
            }
            .launchIn(viewModelScope)

        searchCoordinator.state
            .onEach { coordinatorState ->
                _uiState.value = _uiState.value.copy(
                    searchState = coordinatorState
                )
            }
            .launchIn(viewModelScope)

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

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun search(query: String) {
        _searchQuery.value = query
        
        viewModelScope.launch {
            // If the in-memory profile is null, actively wait and fetch it from the DB
            val profileId = _activeProfile.value?.id 
                ?: getActiveProfileUseCase().firstOrNull()?.id 
                ?: return@launch // If it's STILL null here, the DB is completely empty
                
            searchCoordinator.search(query, profileId)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        searchCoordinator.clear()
    }

    fun clearHistory() {
        viewModelScope.launch {
            val profileId = _activeProfile.value?.id ?: getActiveProfileUseCase().firstOrNull()?.id ?: return@launch
            clearSearchHistoryUseCase(profileId)
        }
    }

    private fun observeHistory(profileId: String) {
        getSearchHistoryUseCase(profileId)
            .onEach { history ->
                _uiState.value = _uiState.value.copy(searchHistory = history)
            }
            .launchIn(viewModelScope)
    }
}

data class SearchUiState(
    val searchState: SearchState = SearchState.Idle,
    val searchHistory: List<String> = emptyList(),
    val activeProfileId: String? = null
)