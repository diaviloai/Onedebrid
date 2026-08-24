package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.Episode
import javax.inject.Inject

/**
 * Resolves a single Episode by its id, given the parent show's mediaId.
 *
 * Introduced in Session 27 for PlayerViewModel, which now receives only
 * primitive nav args (mediaId, an optional episodeId, an optional
 * resumeMs) instead of a full PlaybackRequest via PendingPlaybackHolder —
 * see PlayerViewModel's own doc comment and currentsprint.md's Session 27
 * notes for the full reasoning behind that change.
 *
 * A one-line pass-through to MediaRepository.getEpisodeById(), matching
 * this project's one-use-case-per-operation convention (same shape as
 * GetMediaByIdUseCase, GetEpisodesUseCase).
 */
class GetEpisodeByIdUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(mediaId: String, episodeId: String): RepositoryResult<Episode> =
        mediaRepository.getEpisodeById(mediaId, episodeId)
}