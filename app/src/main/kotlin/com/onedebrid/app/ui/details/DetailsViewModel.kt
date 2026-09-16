package com.onedebrid.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailsUiState {
    object Loading : DetailsUiState
    data class Success(
        val media: Media,
        val streams: List<StreamCandidate> = emptyList(),
        val isLoadingStreams: Boolean = false
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    val mediaType: MediaType = MediaType.valueOf(
        savedStateHandle.get<String>("mediaType")?.uppercase() ?: "MOVIE"
    )
    val mediaId: String = savedStateHandle.get<String>("mediaId") ?: ""
    val initialResumePositionMs: Long? = savedStateHandle.get<Long>("resumePositionMs")?.takeIf { it > 0L }

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadMediaDetails()
    }

    fun loadMediaDetails() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.value = DetailsUiState.Loading
            when (val result = mediaRepository.getMediaDetails(mediaId)) {
                is RepositoryResult.Success -> {
                    val media = result.data
                    _uiState.value = DetailsUiState.Success(
                        media = media,
                        isLoadingStreams = true
                    )
                    loadStreams(media)
                }
                is RepositoryResult.Failure -> {
                    _uiState.value = DetailsUiState.Error("Failed to load media details.")
                }
            }
        }
    }

    private fun loadStreams(media: Media, episode: Episode? = null) {
        viewModelScope.launch(dispatchers.io) {
            when (val result = mediaRepository.searchStreamsByMedia(media, episode)) {
                is RepositoryResult.Success -> {
                    val currentState = _uiState.value
                    if (currentState is DetailsUiState.Success) {
                        _uiState.value = currentState.copy(
                            streams = result.data,
                            isLoadingStreams = false
                        )
                    }
                }
                is RepositoryResult.Failure -> {
                    val currentState = _uiState.value
                    if (currentState is DetailsUiState.Success) {
                        _uiState.value = currentState.copy(
                            isLoadingStreams = false
                        )
                    }
                }
            }
        }
    }
}
