package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import javax.inject.Inject

class ResolvePlaybackUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {

    suspend operator fun invoke(
        request: PlaybackRequest
    ): RepositoryResult<StreamSource> {
        val candidate = request.preferredSource
            ?: return RepositoryResult.Failure(AppError.NoCachedStreamAvailable)

        return mediaRepository.resolveStream(candidate)
    }
}