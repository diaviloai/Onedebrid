package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the current application session.
 *
 * In-memory only. Not persisted. Cleared when the app process ends.
 *
 * The session represents transient application state — what is
 * currently playing, what search is active, which profile is loaded
 * into the session. It is distinct from the Profile, which is the
 * persisted record of user preferences.
 */
interface SessionRepository {

    /**
     * Initialise the session with the active profile.
     *
     * Called once by SessionCoordinator when the app starts,
     * and again whenever the active profile changes.
     *
     * Must be called before any other session operations.
     */
    fun initialise(profile: UserProfile)

    /**
     * Observe the current session state.
     *
     * Emits null if the session has not been initialised yet.
     */
    fun observeSession(): Flow<SessionState?>

    /**
     * Start a new playback session for the given stream.
     */
    fun startPlaybackSession(source: StreamSource)

    /**
     * Update the current playback position within the active session.
     */
    fun updatePlaybackPosition(positionMs: Long)

    /**
     * End the current playback session.
     */
    fun endPlaybackSession()

    /**
     * Update the active search session with a new query and filters.
     */
    fun updateSearchSession(query: String, filters: Map<String, String>)

    /**
     * Clear the active search session.
     */
    fun clearSearchSession()

    /**
     * Clear the entire session.
     *
     * Called on profile switch or sign-out.
     */
    fun clearSession()
}