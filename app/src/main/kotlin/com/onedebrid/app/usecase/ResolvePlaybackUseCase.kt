package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import javax.inject.Inject

/**
 * Resolves a PlaybackRequest into a playable StreamSource.
 *
 * If the request carries a preferredSource (the user manually picked a
 * stream — not yet reachable anywhere in the UI as of Session 27/28/29,
 * since no picker screen exists), that candidate is resolved directly.
 *
 * If preferredSource is null (Smart Defaults — every caller today), this
 * originally failed immediately with NoCachedStreamAvailable rather than
 * actually applying Smart Defaults (fixed Session 28). Session 28's fix
 * searched by the request's Media title via the free-text SearchProvider
 * path — functionally untestable at the time, since StubSearchProvider
 * always failed.
 *
 * Session 29: switched from title-based search to
 * MediaRepository.searchStreamsByMedia(), now that TorrentioSearchProvider
 * exists. This is a more correct fix, not just a swap to a working
 * provider — Torrentio (and torrent-indexer-style backends generally)
 * key on IMDb ID, not free text, so ID-based lookup is the right shape
 * for this call regardless of which provider backs it. See
 * SearchProvider.searchByMedia()'s doc comment and currentsprint.md
 * Session 29 for the full design discussion.
 *
 * This also fixes a real omission from Session 28: request.episode was
 * never passed to the search call, meaning a TV_SHOW PlaybackRequest
 * could never actually resolve via Smart Defaults (Torrentio's series
 * endpoint requires season/episode — see TorrentioSearchProvider.kt).
 * request.episode is now threaded through to searchStreamsByMedia().
 *
 * "First candidate with a hash" is still a deliberately minimal
 * selection rule, not a real ranking algorithm — no profile-based
 * quality-preference signal is surfaced to this layer yet. This
 * selection logic is kept local to this Use Case (not a Repository
 * method, since ranking is business logic, and not yet its own Use
 * Case/reusable component, since Simplicity First per Project_Design.md
 * doesn't justify a new abstraction for ~5 lines of logic) — flagged in
 * currentsprint.md as the point to extract from when the
 * stream-candidate picker UI is built, since the picker will need this
 * same "get candidates for this Media" step, just surfaced to the user
 * instead of auto-applied.
 */
class ResolvePlaybackUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(
        request: PlaybackRequest,
        profileId: String
    ): RepositoryResult<StreamSource> {
        val candidate = request.preferredSource
            ?: return resolveSmartDefault(request)

        return mediaRepository.resolveStream(candidate)
    }

    /**
     * Smart Defaults fallback: look up stream candidates for the
     * request's Media (and Episode, if this is a TV_SHOW) directly by
     * ID, and resolve the first candidate that has a hash (resolveStream
     * requires one — candidates without a hash, e.g. magnet-link-only
     * results, cannot be resolved via the current
     * DebridProvider.resolveStream(hash) contract).
     *
     * profileId is no longer needed here (searchStreamsByMedia() doesn't
     * take one — see MediaRepository.kt's doc comment on why: it looks
     * up an already-known Media rather than performing a
     * profile-scoped/history-tracked free-text search). Dropped from
     * this method's signature accordingly; invoke() no longer passes it
     * through to this call.
     */
    private suspend fun resolveSmartDefault(
        request: PlaybackRequest
    ): RepositoryResult<StreamSource> {
        val searchResult = mediaRepository.searchStreamsByMedia(
            media = request.media,
            episode = request.episode
        )

        val candidates: List<StreamCandidate> = when (searchResult) {
            is RepositoryResult.Success -> searchResult.data
            is RepositoryResult.Failure -> return searchResult
        }

        val candidate = candidates.firstOrNull { it.hash != null }
            ?: return RepositoryResult.Failure(AppError.NoCachedStreamAvailable)

        return mediaRepository.resolveStream(candidate)
    }
}