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
 * stream — not yet reachable anywhere in the UI as of Session 27/28, since
 * no picker screen exists), that candidate is resolved directly.
 *
 * If preferredSource is null (Smart Defaults — every caller today), this
 * previously failed immediately with NoCachedStreamAvailable rather than
 * actually applying Smart Defaults, which meant nothing could ever play
 * without a manual selection despite Project_Design.md's Smart Defaults
 * principle being the intended default behavior everywhere. Fixed in
 * Session 28: the null case now searches by the request's Media title,
 * filters results down to SearchResults for that exact mediaId (a title
 * search can return other matches — e.g. remakes, shows with similar
 * names — and resolving the wrong title's stream would be worse than
 * failing), and resolves the first candidate with a hash from the first
 * matching result.
 *
 * "First candidate with a hash" is a deliberately minimal selection
 * rule, not a real ranking algorithm. There is currently no real
 * SearchProvider wired in (StubSearchProvider always fails) and no
 * profile-based quality-preference signal surfaced to this layer yet, so
 * building a more sophisticated scorer now would be guessing at criteria
 * rather than implementing anything real. This selection logic is kept
 * local to this Use Case (not a Repository method, since ranking is
 * business logic, and not yet its own Use Case/reusable component, since
 * Simplicity First per Project_Design.md doesn't justify a new
 * abstraction for ~5 lines of logic) — flagged in currentsprint.md as the
 * point to extract from when the stream-candidate picker UI is built,
 * since the picker will need this same "get candidates for this Media"
 * step, just surfaced to the user instead of auto-applied.
 */
class ResolvePlaybackUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(
        request: PlaybackRequest,
        profileId: String
    ): RepositoryResult<StreamSource> {
        val candidate = request.preferredSource
            ?: return resolveSmartDefault(request, profileId)

        return mediaRepository.resolveStream(candidate)
    }

    /**
     * Smart Defaults fallback: search for the request's Media by title,
     * restrict to results for the same mediaId, and resolve the first
     * candidate that has a hash (resolveStream requires one — candidates
     * without a hash, e.g. magnet-link-only results, cannot be resolved
     * via the current DebridProvider.resolveStream(hash) contract).
     */
    private suspend fun resolveSmartDefault(
        request: PlaybackRequest,
        profileId: String
    ): RepositoryResult<StreamSource> {
        val searchResult = mediaRepository.search(
            query = request.media.title,
            profileId = profileId
        )

        val results = when (searchResult) {
            is RepositoryResult.Success -> searchResult.data
            is RepositoryResult.Failure -> return searchResult
        }

        val candidate: StreamCandidate = results
            .firstOrNull { it.media.id == request.media.id }
            ?.candidates
            ?.firstOrNull { it.hash != null }
            ?: return RepositoryResult.Failure(AppError.NoCachedStreamAvailable)

        return mediaRepository.resolveStream(candidate)
    }
}