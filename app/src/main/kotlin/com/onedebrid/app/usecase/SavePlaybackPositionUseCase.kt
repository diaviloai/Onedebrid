package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Updates the current playback position within the active in-memory session.
 *
 * Wraps SessionRepository.updatePlaybackPosition(), which is a no-op if
 * there is no active playback session (SessionRepositoryImpl returns early
 * if session or playback is null). This use case exists so the Player
 * screen has something to call as ExoPlayer reports position updates,
 * without reaching into SessionRepository directly, per the Use Case /
 * Repository boundary in Internal_API_Specification.md.
 *
 * Note: this only updates the in-memory SessionState. It does not persist
 * to Continue Watching (Room). Continue Watching progress is a separate,
 * profile-scoped concern owned by PlaybackRepository and is not wired up
 * by this use case — that remains a further step, not assumed here.
 */
class SavePlaybackPositionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(positionMs: Long) {
        withContext(dispatchers.io) {
            sessionRepository.updatePlaybackPosition(positionMs)
        }
    }
}