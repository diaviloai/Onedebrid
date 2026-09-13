package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.StreamCandidate
import javax.inject.Inject

/**
 * Fetches stream candidates for an already-known Media (and Episode, if
 * applicable), without resolving any of them through Real-Debrid.
 *
 * A one-line pass-through to MediaRepository.searchStreamsByMedia(), same
 * shape as GetEpisodesUseCase — kept as its own Use Case rather than
 * inlined into a ViewModel because Internal_API_Specification.md's Design
 * Rules are explicit that ViewModels never access Repositories directly;
 * only Use Cases and Coordinators may. See MediaRepository.
 * searchStreamsByMedia()'s own doc comment for the full reasoning on why
 * this is ID-based rather than free-text (it looks up an already-known
 * Media, not a text query).
 *
 * Added for the stream-candidate picker feature (DetailsViewModel).
 * ResolvePlaybackUseCase.resolveSmartDefault() calls
 * MediaRepository.searchStreamsByMedia() directly rather than through this
 * Use Case — that call site already existed (Session 29) before this Use
 * Case did, and its own doc comment flags itself as the natural extraction
 * point for this exact "get candidates for this Media" step once a picker
 * needed it. Left as-is rather than retrofitted to route through this new
 * Use Case, to keep this session's diff to DetailsViewModel/DetailsScreen
 * additive rather than touching ResolvePlaybackUseCase's already-working,
 * already-tested resolve path for a purely cosmetic consistency gain.
 */
class GetStreamCandidatesUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(
        media: Media,
        episode: Episode? = null
    ): RepositoryResult<List<StreamCandidate>> =
        mediaRepository.searchStreamsByMedia(media, episode)
}