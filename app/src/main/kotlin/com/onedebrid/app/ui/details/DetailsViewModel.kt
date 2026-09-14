package com.onedebrid.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.ui.navigation.PlayerNavArgs
import com.onedebrid.app.usecase.GetEpisodesUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.GetStreamCandidatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val media: Media? = null,
    val isLoadingMedia: Boolean = true,
    val mediaError: AppError? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodesError: AppError? = null,
    val picker: PickerUiState = PickerUiState.Closed
)

sealed interface PickerUiState {
    data object Closed : PickerUiState
    data class Loading(val episode: Episode?) : PickerUiState
    data class Loaded(val episode: Episode?, val candidates: List<StreamCandidate>) : PickerUiState
    data class Error(val episode: Episode?, val error: AppError) : PickerUiState
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val getStreamCandidatesUseCase: GetStreamCandidatesUseCase
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle["mediaId"]) {
        "DetailsScreen requires a mediaId nav argument"
    }

    val initialResumePositionMs: Long? = savedStateHandle.get<Long>("resumePositionMs")?.takeIf { it > 0L }

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _navigateToPlayer = Channel<PlayerNavArgs>(Channel.BUFFERED)
    val navigateToPlayer: Flow<PlayerNavArgs> = _navigateToPlayer.receiveAsFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            when (val result = getMediaByIdUseCase(mediaId)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        media = result.data,
                        isLoadingMedia = false
                    )
                    if (result.data.type == MediaType.TV_SHOW) {
                        loadEpisodes()
                    }
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMedia = false,
                        mediaError = result.error
                    )
                }
            }
        }
    }

    private fun loadEpisodes() {
        _uiState.value = _uiState.value.copy(isLoadingEpisodes = true, episodesError = null)
        viewModelScope.launch {
            when (val result = getEpisodesUseCase(mediaId)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        episodes = result.data,
                        isLoadingEpisodes = false
                    )
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingEpisodes = false,
                        episodesError = result.error
                    )
                }
            }
        }
    }

    fun retryMedia() {
        _uiState.value = _uiState.value.copy(isLoadingMedia = true, mediaError = null)
        loadMedia()
    }

    fun retryEpisodes() {
        loadEpisodes()
    }

    fun onPlayMovie() {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = null,
                resumeMs = initialResumePositionMs,
                preferredSource = null
            )
        )
    }

    fun onPlayEpisode(episode: Episode) {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = episode.id,
                resumeMs = initialResumePositionMs,
                preferredSource = null
            )
        )
    }

    fun onChooseStream(episode: Episode? = null) {
        val media = _uiState.value.media ?: return
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Loading(episode))
        viewModelScope.launch {
            when (val result = getStreamCandidatesUseCase(media, episode)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        picker = PickerUiState.Loaded(episode, result.data)
                    )
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        picker = PickerUiState.Error(episode, result.error)
                    )
                }
            }
        }
    }

    fun retryChooseStream() {
        val currentPicker = _uiState.value.picker
        val episode = when (currentPicker) {
            is PickerUiState.Error -> currentPicker.episode
            is PickerUiState.Loading -> currentPicker.episode
            is PickerUiState.Loaded -> currentPicker.episode
            PickerUiState.Closed -> null
        }
        onChooseStream(episode)
    }

    fun onDismissPicker() {
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Closed)
    }

    fun onCandidateSelected(candidate: StreamCandidate) {
        val media = _uiState.value.media ?: return
        val loadedPicker = _uiState.value.picker as? PickerUiState.Loaded ?: return
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Closed)
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = loadedPicker.episode?.id,
                resumeMs = initialResumePositionMs,
                preferredSource = candidate.url
            )
        )
    }
}
