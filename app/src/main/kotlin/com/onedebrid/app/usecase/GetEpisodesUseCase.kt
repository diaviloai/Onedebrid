package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.Episode
import javax.inject.Inject

/**
 * Fetches the full episode list for a TV show, keyed by mediaId.
 *
 * A one-line pass-through to MediaRepository.getEpisodes(), same shape as
 * GetMediaByIdUseCase — kept as its own Use Case rather than folded into
 * that one because it represents a distinct business operation (episode
 * listing vs. media detail lookup) per Internal_API_Specification.md's
 * "each Use Case represents one business operation" rule, even though the
 * implementation is trivial today.
 *
 * Returns all episodes across all seasons; DetailsScreen groups/filters by
 * season for display. See MediaRepository.getEpisodes()'s own doc comment
 * for why this returns everything rather than accepting a season filter.
 */
class GetEpisodesUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(mediaId: String): RepositoryResult<List<Episode>> =
        mediaRepository.getEpisodes(mediaId)
}