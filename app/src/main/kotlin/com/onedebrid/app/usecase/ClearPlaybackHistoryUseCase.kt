package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ClearPlaybackHistoryUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(profileId: String) {
        withContext(dispatchers.io) {
            playbackRepository.clearHistory(profileId)
        }
    }
}