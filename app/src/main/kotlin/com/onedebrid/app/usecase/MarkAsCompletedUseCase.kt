package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MarkAsCompletedUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        profileId: String,
        mediaId: String,
        episodeId: String? = null
    ) {
        withContext(dispatchers.io) {
            playbackRepository.markAsCompleted(
                profileId = profileId,
                mediaId = mediaId,
                episodeId = episodeId
            )
        }
    }
}