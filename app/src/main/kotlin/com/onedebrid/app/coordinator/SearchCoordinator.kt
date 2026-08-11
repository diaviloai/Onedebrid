package com.onedebrid.app.coordinator

import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.ApplicationScope
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.usecase.SearchMediaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the search workflow.
 *
 * Accepts a query, executes it via SearchMediaUseCase, and exposes
 * observable state so the search ViewModel can react without polling.
 *
 * Cancels any in-progress search if a new query arrives or the
 * search is cleared.
 */
@Singleton
class SearchCoordinator @Inject constructor(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val dispatchers: CoroutineDispatchers,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var activeJob: Job? = null

    /**
     * Execute a search for the given query.
     *
     * Cancels any in-progress search before starting.
     * The query is saved to search history automatically inside
     * SearchMediaUseCase — the Coordinator does not need to handle that.
     */
    fun search(query: String, profileId: String) {
        activeJob?.cancel()
        activeJob = scope.launch(dispatchers.default) {
            _state.value = SearchState.Searching

            when (val result = searchMediaUseCase(query, profileId)) {
                is RepositoryResult.Success -> {
                    _state.value = SearchState.Results(result.data)
                }
                is RepositoryResult.Failure -> {
                    _state.value = SearchState.Error(result.error)
                }
            }
        }
    }

    /**
     * Clear the active search and reset state to Idle.
     *
     * Called when the user clears the search bar or navigates away.
     */
    fun clear() {
        activeJob?.cancel()
        _state.value = SearchState.Idle
    }
}

/**
 * Represents the current state of the search workflow.
 */
sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Results(val results: List<SearchResult>) : SearchState
    data class Error(val error: AppError) : SearchState
}