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