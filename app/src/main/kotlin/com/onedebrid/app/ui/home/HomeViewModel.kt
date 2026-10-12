package com.onedebrid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.ui.navigation.PlayerNavArgs
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinueWatchingUiItem(
    val watchedItem: WatchedItem,
    val media: Media?
)

data class HomeUiState(
    val continueWatching: List<ContinueWatchingUiItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val getContinueWatchingUseCase: GetContinueWatchingUseCase,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigateToPlayer = Channel<PlayerNavArgs>(Channel.BUFFERED)
    val navigateToPlayer: Flow<PlayerNavArgs> = _navigateToPlayer.receiveAsFlow()

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
        continueWatchingJob?.cancel()
        continueWatchingJob = getContinueWatchingUseCase(profileId)
            .map { items ->
                // Fetch the full Media details (Title, Poster) for each watched item.
                // This is fast because MediaRepositoryImpl checks the local MediaCache first.
                items.map { item ->
                    val media = when (val result = getMediaByIdUseCase(item.mediaId)) {
                        is RepositoryResult.Success -> result.data
                        else -> null
                    }
                    ContinueWatchingUiItem(item, media)
                }
            }
            .onEach { enrichedItems ->
                _uiState.value = _uiState.value.copy(
                    continueWatching = enrichedItems,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    fun removeItem(mediaId: String) {
        val profileId = _activeProfile.value?.id ?: return
        viewModelScope.launch {
            removeFromContinueWatchingUseCase(
                profileId = profileId,
                mediaId = mediaId
            )
        }
    }

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