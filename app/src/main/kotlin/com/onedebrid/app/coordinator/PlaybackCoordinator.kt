package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.ApplicationScope
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.usecase.RecordPlaybackUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.usecase.StartPlaybackUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the playback workflow.
 *
 * Accepts a PlaybackRequest, resolves it to a StreamSource,
 * registers the session, and records history. Exposes observable
 * state so the player ViewModel can react without polling.
 *
 * Cancels any in-progress resolution if a new request arrives.
 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val resolvePlaybackUseCase: ResolvePlaybackUseCase,
    private val startPlaybackUseCase: StartPlaybackUseCase,
    private val recordPlaybackUseCase: RecordPlaybackUseCase,
    private val dispatchers: CoroutineDispatchers,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var activeJob: Job? = null

    /**
     * Begin the playback workflow for the given request.
     *
     * Cancels any in-progress resolution before starting.
     */
    fun play(request: PlaybackRequest, profileId: String) {
        activeJob?.cancel()
        activeJob = scope.launch(dispatchers.default) {
            _state.value = PlaybackState.Resolving

            when (val result = resolvePlaybackUseCase(request)) {
                is RepositoryResult.Success -> {
                    val source = result.data

                    when (val sessionResult = startPlaybackUseCase(request, source)) {
                        is RepositoryResult.Success -> {
                            recordPlaybackUseCase(
                                profileId = profileId,
                                mediaId = request.media.id,
                                episodeId = request.episode?.id
                            )
                            _state.value = PlaybackState.Ready(source)
                        }
                        is RepositoryResult.Failure -> {
                            _state.value = PlaybackState.Error(sessionResult.error)
                        }
                    }
                }
                is RepositoryResult.Failure -> {
                    _state.value = PlaybackState.Error(result.error)
                }
            }
        }
    }

    /**
     * Stop the current playback session and reset state.
     */
    fun stop() {
        activeJob?.cancel()
        _state.value = PlaybackState.Idle
    }
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Resolving : PlaybackState
    data class Ready(val source: StreamSource) : PlaybackState
    data class Error(val error: AppError) : PlaybackState
}