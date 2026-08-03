package com.onedebrid.app.data.repository

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

    private val _session = MutableStateFlow<SessionState?>(null)

    override fun initialise(profile: UserProfile) {
        _session.value = SessionState(activeProfile = profile)
    }

    override fun observeSession(): Flow<SessionState> =
        _session.asStateFlow().filterNotNull()

    override suspend fun startPlaybackSession(
        request: PlaybackRequest,
        stream: StreamSource
    ) {
        _session.value = _session.value?.copy(
            playback = PlaybackSession(
                media = request.media,
                episode = request.episode,
                streamSource = stream,
                positionMs = request.resumePositionMs ?: 0L,
                state = PlaybackState.IDLE
            )
        )
    }

    override suspend fun updatePlaybackPosition(positionMs: Long) {
        val current = _session.value ?: return
        val currentPlayback = current.playback ?: return
        _session.value = current.copy(
            playback = currentPlayback.copy(positionMs = positionMs)
        )
    }

    override suspend fun endPlaybackSession() {
        _session.value = _session.value?.copy(playback = null)
    }

    override suspend fun updateSearchSession(
        query: String,
        filters: Map<String, String>
    ) {
        val current = _session.value ?: return
        _session.value = current.copy(
            search = SearchSession(
                query = query,
                results = current.search?.results ?: emptyList(),
                isLoading = false
            )
        )
    }

    override suspend fun clearSearchSession() {
        _session.value = _session.value?.copy(search = null)
    }

    override suspend fun clearSession() {
        _session.value = null
    }
}