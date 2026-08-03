package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StartPlaybackUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        request: PlaybackRequest,
        stream: StreamSource
    ): RepositoryResult<Unit> = withContext(dispatchers.io) {
        try {
            sessionRepository.startPlaybackSession(
                request = request,
                stream = stream
            )
            RepositoryResult.Success(Unit)
        } catch (e: Exception) {
            RepositoryResult.Failure(
                com.onedebrid.app.domain.error.AppError.Unknown(
                    message = "Failed to start playback session: ${e.message}"
                )
            )
        }
    }
}