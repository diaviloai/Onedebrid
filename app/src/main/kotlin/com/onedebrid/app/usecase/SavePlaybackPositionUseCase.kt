package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Updates the current playback position, both in the active in-memory
 * session and in Room-backed Continue Watching.
 *
 * Two writes happen here, not one:
 * 1. SessionRepository.updatePlaybackPosition() — updates the in-memory
 *    SessionState.playback.positionMs so anything observing the live
 *    session (e.g. a future "now playing" indicator) sees the current
 *    position immediately. No-ops if there is no active playback session.
 * 2. PlaybackRepository.saveProgress() — persists the position to the
 *    Room-backed ContinueWatchingEntity table, so progress survives app
 *    restarts and shows up in HomeScreen's Continue Watching list. This
 *    was the gap this use case was missing until now (see
 *    currentsprint.md, Session 24) — SessionState alone is in-memory only
 *    and is lost on process death.
 *
 * profileId, mediaId, episodeId, seasonNumber, and episodeNumber are all
 * read from SessionRepository.getCurrentSession() rather than being passed
 * in as parameters — the Player screen already establishes these when
 * playback starts (see SessionRepositoryImpl.startPlaybackSession()), and
 * requiring PlayerViewModel to thread them through on every single
 * position-save call (every 5 seconds while playing, per
 * POSITION_SAVE_INTERVAL_MS in PlayerViewModel) would be redundant —
 * they don't change for the lifetime of a single playback session.
 *
 * If getCurrentSession() returns null, or its playback field is null (no
 * active playback session), this use case only performs the in-memory
 * update via SessionRepository — which is itself a no-op in that same
 * case (see SessionRepositoryImpl.updatePlaybackPosition()) — and skips
 * the Room write entirely, since there is nothing meaningful to persist
 * (no mediaId to attach the position to). This is not expected to happen
 * in normal use, since the Player screen only calls this use case after
 * play() has already started a session, but is handled explicitly rather
 * than assumed away.
 *
 * durationMs comes from the caller (PlayerViewModel, ultimately
 * ExoPlayer.duration read in PlayerScreen.kt) because — unlike
 * profileId/mediaId/episodeId — no domain model in this codebase carries
 * media duration. ExoPlayer is the only source of truth for it, and only
 * the Compose player screen holds the ExoPlayer instance (see
 * Technical_standards.md's UI-layer boundary rule: ViewModels never touch
 * ExoPlayer directly).
 */
class SavePlaybackPositionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(positionMs: Long, durationMs: Long) {
        withContext(dispatchers.io) {
            sessionRepository.updatePlaybackPosition(positionMs)

            val session = sessionRepository.getCurrentSession() ?: return@withContext
            val playback = session.playback ?: return@withContext

            playbackRepository.saveProgress(
                profileId = session.activeProfile.id,
                mediaId = playback.media.id,
                episodeId = playback.episode?.id,
                seasonNumber = playback.episode?.seasonNumber,
                episodeNumber = playback.episode?.episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }
}