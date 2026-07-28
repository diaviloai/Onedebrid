package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamSource
import kotlinx.coroutines.flow.Flow

/**
 * Repository for active application session state.
 *
 * This repository is in-memory only. Data is not persisted to the
 * database and does not survive process death. This is intentional —
 * session state represents what the user is doing right now, not
 * what they have done historically.
 *
 * Owned by the Session System.
 */
interface SessionRepository {

    /**
     * Observe the full current session state.
     *
     * Emits whenever any part of the session changes. Screens that
     * only need a specific slice (e.g. active playback) should map
     * this flow rather than observing the whole state.
     */
    fun observeSession(): Flow<SessionState>

    /**
     * Begin a new playback session.
     *
     * Called when the user initiates playback. Stores the active
     * request and resolved stream so the Playback Coordinator can
     * access them without being passed the full objects through
     * every call.
     */
    suspend fun startPlaybackSession(
        request: PlaybackRequest,
        stream: StreamSource
    )

    /**
     * Update the playback position within the current session.
     *
     * Called frequently during playback. Kept separate from
     * startPlaybackSession to avoid rebuilding the full session
     * object on every position update.
     */
    suspend fun updatePlaybackPosition(positionMs: Long)

    /**
     * End the current playback session.
     *
     * Called when the player is closed or playback completes.
     * Clears the active stream and request from session state.
     */
    suspend fun endPlaybackSession()

    /**
     * Update the active search query and filters.
     *
     * Called as the user types or adjusts filters on the search screen.
     */
    suspend fun updateSearchSession(query: String, filters: Map<String, String>)

    /**
     * Clear the active search session.
     *
     * Called when the user leaves the search screen or clears their query.
     */
    suspend fun clearSearchSession()

    /**
     * Reset the entire session to its default empty state.
     *
     * Called on profile switch or explicit sign-out.
     */
    suspend fun clearSession()
}