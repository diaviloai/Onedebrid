package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.domain.model.WatchedItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContinueWatchingUseCase @Inject constructor(
    private val playbackRepository: PlaybackRepository
) {

    operator fun invoke(profileId: String): Flow<List<WatchedItem>> =
        playbackRepository.observeContinueWatching(profileId)
}