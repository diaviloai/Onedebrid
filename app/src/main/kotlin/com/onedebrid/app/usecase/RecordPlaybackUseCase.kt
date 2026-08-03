package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RecordPlaybackUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        profileId: String,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        episodeId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        withContext(dispatchers.io) {
            playbackRepository.recordPlayed(
                profileId = profileId,
                mediaId = mediaId,
                positionMs = positionMs,
                durationMs = durationMs,
                episodeId = episodeId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber
            )
        }
    }
}