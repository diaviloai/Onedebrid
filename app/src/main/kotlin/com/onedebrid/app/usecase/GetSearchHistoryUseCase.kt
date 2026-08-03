package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchHistoryUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {

    operator fun invoke(profileId: String): Flow<List<String>> =
        searchRepository.observeSearchHistory(profileId)
}