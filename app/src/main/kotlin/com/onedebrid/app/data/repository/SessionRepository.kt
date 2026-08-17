package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the current application session.
 *
 * In-memory only. Not persisted. Cleared when the app process ends.
 */
interface SessionRepository {

    /**
     * Initialise the session with the active profile.
     *
     * Called once by SessionCoordinator when the app starts,
     * and again whenever the active profile changes.
     */
    fun initialise(profile: UserProfile)

    /**
     * Observe the current session state.
     */
    fun observeSession(): Flow<SessionState>

    /**
     * Synchronously read the current session state without collecting a Flow.
     *
     * Returns null if the session has not been initialised yet (initialise()
     * has not been called), matching observeSession()'s underlying null-until-
     * initialised state before its filterNotNull(). Intended for call sites
     * that need a one-off snapshot rather than an ongoing subscription — e.g.
     * SavePlaybackPositionUseCase reading profileId/mediaId/episodeId once per
     * position-save call, where collecting a Flow would be unnecessary
     * overhead for a value that's only read, not observed.
     */
    fun getCurrentSession(): SessionState?

    /**
     * Start a new playback session for the given request and resolved stream.
     */
    suspend fun startPlaybackSession(request: PlaybackRequest, stream: StreamSource)

    /**
     * Update the current playback position within the active session.
     */
    suspend fun updatePlaybackPosition(positionMs: Long)

    /**
     * End the current playback session.
     */
    suspend fun endPlaybackSession()

    /**
     * Update the active search session with a new query and filters.
     */
    suspend fun updateSearchSession(query: String, filters: Map<String, String>)

    /**
     * Clear the active search session.
     */
    suspend fun clearSearchSession()

    /**
     * Clear the entire session.
     */
    suspend fun clearSession()
}