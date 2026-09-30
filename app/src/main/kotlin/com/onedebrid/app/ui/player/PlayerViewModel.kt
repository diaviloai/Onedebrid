package com.onedebrid.app.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.usecase.SavePlaybackPositionUseCase
import com.onedebrid.app.usecase.EndPlaybackSessionUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetEpisodeByIdUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackCoordinator: PlaybackCoordinator,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getEpisodeByIdUseCase: GetEpisodeByIdUseCase,
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val endPlaybackSessionUseCase: EndPlaybackSessionUseCase
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle["mediaId"]) {
        "PlayerScreen requires a mediaId nav argument"
    }

    private val episodeId: String? =
        (savedStateHandle["episodeId"] as? String)?.takeIf { it != "none" }
    private val resumePositionMs: Long? =
        (savedStateHandle["resumeMs"] as? Long)?.takeIf { it != -1L }

    private val preferredSource: StreamCandidate? =
        decodePreferredSource(savedStateHandle["preferredSource"] as? String)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var lastKnownPositionMs: Long = 0L
    private var lastKnownDurationMs: Long = 0L

    private var positionSaveJob: Job? = null
    private var activeProfileId: String? = null

    init {
        playbackCoordinator.state
            .onEach { coordinatorState ->
                _uiState.value = _uiState.value.copy(coordinatorState = coordinatorState)
            }
            .launchIn(viewModelScope)

        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        _uiState.value = _uiState.value.copy(resolveState = ResolveState.Resolving)

        viewModelScope.launch {
            val profile = getActiveProfileUseCase().first()
            activeProfileId = profile.id

            val mediaResult = getMediaByIdUseCase(mediaId)
            val media = when (mediaResult) {
                is RepositoryResult.Success -> mediaResult.data
                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        resolveState = ResolveState.Error(mediaResult.error)
                    )
                    return@launch
                }
            }

            val episode: Episode? = if (episodeId != null) {
                when (val episodeResult = getEpisodeByIdUseCase(mediaId, episodeId)) {
                    is RepositoryResult.Success -> episodeResult.data
                    is RepositoryResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            resolveState = ResolveState.Error(episodeResult.error)
                        )
                        return@launch
                    }
                }
            } else {
                null
            }

            _uiState.value = _uiState.value.copy(resolveState = ResolveState.Resolved)

            val request = PlaybackRequest(
                media = media,
                episode = episode,
                preferredSource = preferredSource,
                resumePositionMs = resumePositionMs
            )
            playbackCoordinator.play(request, profile.id)
        }
    }

    fun retryResolve() {
        resolveAndPlay()
    }

    fun retryPlay() {
        val profileId = activeProfileId ?: return
        viewModelScope.launch {
            val mediaResult = getMediaByIdUseCase(mediaId)
            val media = (mediaResult as? RepositoryResult.Success)?.data ?: return@launch
            val episode = episodeId?.let {
                (getEpisodeByIdUseCase(mediaId, it) as? RepositoryResult.Success)?.data
            }
            val request = PlaybackRequest(
                media = media,
                episode = episode,
                preferredSource = preferredSource,
                resumePositionMs = resumePositionMs
            )
            playbackCoordinator.play(request, profileId)
        }
    }

    fun stop() {
        stopPositionSaving()
        playbackCoordinator.stop()
        viewModelScope.launch {
            endPlaybackSessionUseCase()
        }
    }

    fun onPlayerStateChanged(newState: PlayerLifecycleState, positionMs: Long, durationMs: Long) {
        lastKnownPositionMs = positionMs
        lastKnownDurationMs = durationMs
        _uiState.value = _uiState.value.copy(playerLifecycleState = newState)

        when (newState) {
            PlayerLifecycleState.PLAYING -> startPositionSaving()
            PlayerLifecycleState.PAUSED, PlayerLifecycleState.ENDED -> {
                stopPositionSaving()
                viewModelScope.launch {
                    savePlaybackPositionUseCase(positionMs, durationMs)
                }
            }
            else -> stopPositionSaving()
        }
    }

    private fun startPositionSaving() {
        if (positionSaveJob?.isActive == true) return
        positionSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (isActive) {
                    savePlaybackPositionUseCase(lastKnownPositionMs, lastKnownDurationMs)
                }
            }
        }
    }

    private fun stopPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    override fun onCleared() {
        stopPositionSaving()
        super.onCleared()
    }
}

private const val POSITION_SAVE_INTERVAL_MS = 5_000L

private fun decodePreferredSource(rawArg: String?): StreamCandidate? {
    if (rawArg.isNullOrEmpty()) return null
    return try {
        Json.decodeFromString<StreamCandidate>(Uri.decode(rawArg))
    } catch (e: SerializationException) {
        null
    }
}

sealed interface ResolveState {
    data object Resolving : ResolveState
    data object Resolved : ResolveState
    data class Error(val error: AppError) : ResolveState
}

data class PlayerUiState(
    val resolveState: ResolveState = ResolveState.Resolving,
    val coordinatorState: CoordinatorState = CoordinatorState.Idle,
    val playerLifecycleState: PlayerLifecycleState = PlayerLifecycleState.IDLE
)