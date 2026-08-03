package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.SearchResult
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val searchRepository: SearchRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        query: String,
        profileId: String
    ): RepositoryResult<SearchResult> = withContext(dispatchers.io) {

        // Save to history regardless of search outcome.
        // If the history write fails, swallow the error — a history
        // persistence failure must never block or fail the search itself.
        try {
            searchRepository.addSearchQuery(profileId, query)
        } catch (e: Exception) {
            // Intentionally ignored.
        }

        mediaRepository.search(query, profileId)
    }
}