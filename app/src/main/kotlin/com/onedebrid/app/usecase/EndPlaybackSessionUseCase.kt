package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Ends the current in-memory playback session.
 *
 * Wraps SessionRepository.endPlaybackSession(), which clears the playback
 * field on SessionState (SessionRepositoryImpl sets it to null; the rest
 * of the session — active profile, search — is untouched). Intended to be
 * called by the Player screen when playback is stopped or the user
 * navigates away, so the session no longer reports a stale active
 * PlaybackSession.
 *
 * Distinct from PlaybackCoordinator.stop(), which resets the coordinator's
 * own resolve/start workflow state (CoordinatorState back to Idle) but
 * does not touch SessionRepository. Both will likely be called together
 * from the Player screen's teardown path — that wiring is not decided
 * here, only the use case itself.
 */
class EndPlaybackSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke() {
        withContext(dispatchers.io) {
            sessionRepository.endPlaybackSession()
        }
    }
}