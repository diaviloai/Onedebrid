package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.Media
import javax.inject.Inject

/**
 * Resolves a bare mediaId (e.g. from a WatchedItem, which carries no full
 * Media object per its own doc comment) back into a full Media, via
 * MediaRepository.getMediaDetails() — cache-first as of Session 25's
 * MediaCache wiring in MediaRepositoryImpl.
 *
 * Introduced for HomeViewModel's tap-to-resume flow (Session 25), but
 * intentionally not HomeViewModel-specific — any future caller needing to
 * turn a mediaId into a Media (e.g. a Details screen, Next Steps #2) should
 * use this rather than calling MediaRepository directly, consistent with
 * the project's one-use-case-per-operation convention.
 */
class GetMediaByIdUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(mediaId: String): RepositoryResult<Media> =
        mediaRepository.getMediaDetails(mediaId)
}