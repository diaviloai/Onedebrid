package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initiates a new playback session.
 *
 * Records that the media was played in [PlaybackRepository] and initializes
 * the active session state in [SessionRepository] with the provided [PlaybackRequest]
 * and resolved [StreamSource].
 */
@Singleton
class StartPlaybackSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        request: PlaybackRequest,
        stream: StreamSource
    ): RepositoryResult<Unit> = withContext(dispatchers.io) {
        val currentSession = sessionRepository.getCurrentSession()
            ?: return@withContext RepositoryResult.Failure(AppError.NotAuthenticated)

        val profileId = currentSession.activeProfile.id

        playbackRepository.recordPlayed(
            profileId = profileId,
            mediaId = request.media.id,
            episodeId = request.episode?.id,
            seasonNumber = request.episode?.seasonNumber,
            episodeNumber = request.episode?.episodeNumber
        )

        sessionRepository.startPlaybackSession(request, stream)

        RepositoryResult.Success(Unit)
    }
}
