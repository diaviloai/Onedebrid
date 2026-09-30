package com.onedebrid.app.coordinator

import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.usecase.RecordPlaybackUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.domain.usecase.StartPlaybackSessionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Resolving(val request: PlaybackRequest) : PlaybackState
    data class Ready(val request: PlaybackRequest, val stream: StreamSource) : PlaybackState
    data class Error(val request: PlaybackRequest, val error: AppError) : PlaybackState
}

@Singleton
class PlaybackCoordinator @Inject constructor(
    private val resolvePlaybackUseCase: ResolvePlaybackUseCase,
    private val startPlaybackSessionUseCase: StartPlaybackSessionUseCase,
    private val recordPlaybackUseCase: RecordPlaybackUseCase,
    private val dispatchers: CoroutineDispatchers,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun play(request: PlaybackRequest, profileId: String) {
        currentJob?.cancel()
        _state.value = PlaybackState.Resolving(request)

        currentJob = scope.launch(dispatchers.main) {
            when (val result = resolvePlaybackUseCase(request)) {
                is RepositoryResult.Success -> {
                    val stream = result.data
                    startPlaybackSessionUseCase(request, stream)
                    recordPlaybackUseCase(request, profileId)
                    _state.value = PlaybackState.Ready(request, stream)
                }
                is RepositoryResult.Failure -> {
                    _state.value = PlaybackState.Error(request, result.error)
                }
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        _state.value = PlaybackState.Idle
    }
}
