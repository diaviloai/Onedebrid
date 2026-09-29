package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves current playback position and updates the active session.
 *
 * Called periodically during video playback. Reads active media information
 * directly from [SessionRepository] and updates both session state and [PlaybackRepository].
 */
@Singleton
class SavePlaybackPositionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        positionMs: Long,
        durationMs: Long
    ): RepositoryResult<Unit> = withContext(dispatchers.io) {
        val session = sessionRepository.getCurrentSession()
            ?: return@withContext RepositoryResult.Failure(AppError.NotAuthenticated)

        val playback = session.playback
            ?: return@withContext RepositoryResult.Failure(
                AppError.Unknown("No active playback session found.")
            )

        val profileId = session.activeProfile.id

        playbackRepository.saveProgress(
            profileId = profileId,
            mediaId = playback.media.id,
            episodeId = playback.episode?.id,
            seasonNumber = playback.episode?.seasonNumber,
            episodeNumber = playback.episode?.episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs
        )

        sessionRepository.updatePlaybackPosition(positionMs)

        RepositoryResult.Success(Unit)
    }
}
