package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ClearSearchHistoryUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(profileId: String) {
        withContext(dispatchers.io) {
            searchRepository.clearSearchHistory(profileId)
        }
    }
}