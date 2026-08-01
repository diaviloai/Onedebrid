package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackSession
import com.onedebrid.app.domain.model.PlaybackState
import com.onedebrid.app.domain.model.SearchSession
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor() : SessionRepository {

    /**
     * Internal state is nullable. A null value means no session has been
     * initialised yet — the app is starting up or has just switched profiles.
     * observeSession() filters nulls so callers only receive valid states.
     */
    private val _session = MutableStateFlow<SessionState?>(null)

    /**
     * Called by the Session Coordinator after the active profile is loaded.
     * Must be called before any other session operations are meaningful.
     * Not part of the public interface — the coordinator calls this directly
     * on the implementation during app startup and profile switch.
     */
    fun initialise(profile: UserProfile) {
        _session.value = SessionState(activeProfile = profile)
    }

    override fun observeSession(): Flow<SessionState> =
        _session.asStateFlow().filterNotNull()

    override suspend fun startPlaybackSession(
        request: PlaybackRequest,
        stream: StreamSource
    ) {
        _session.update { current ->
            current?.copy(
                playback = PlaybackSession(
                    media = request.media,
                    episode = request.episode,
                    streamSource = stream,
                    positionMs = request.resumePositionMs ?: 0L,
                    state = PlaybackState.IDLE
                )
            )
        }
    }

    override suspend fun updatePlaybackPosition(positionMs: Long) {
        _session.update { current ->
            val currentPlayback = current?.playback ?: return@update current
            current.copy(
                playback = currentPlayback.copy(positionMs = positionMs)
            )
        }
    }

    override suspend fun endPlaybackSession() {
        _session.update { current ->
            current?.copy(playback = null)
        }
    }

    override suspend fun updateSearchSession(
        query: String,
        filters: Map<String, String>
    ) {
        _session.update { current ->
            current?.copy(
                search = SearchSession(
                    query = query,
                    results = current.search?.results ?: emptyList(),
                    isLoading = false
                )
            )
        }
    }

    override suspend fun clearSearchSession() {
        _session.update { current ->
            current?.copy(search = null)
        }
    }

    override suspend fun clearSession() {
        _session.value = null
    }
}