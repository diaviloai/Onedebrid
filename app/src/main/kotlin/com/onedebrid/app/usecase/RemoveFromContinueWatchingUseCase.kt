package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Removes a single item from a profile's Continue Watching list.
 *
 * Distinct from ClearPlaybackHistoryUseCase, which wipes both Continue
 * Watching and Recently Played for a profile. This targets one mediaId,
 * used when the user explicitly dismisses a single Continue Watching entry.
 */
class RemoveFromContinueWatchingUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        profileId: String,
        mediaId: String
    ) {
        withContext(dispatchers.io) {
            playbackRepository.removeFromContinueWatching(
                profileId = profileId,
                mediaId = mediaId
            )
        }
    }
}