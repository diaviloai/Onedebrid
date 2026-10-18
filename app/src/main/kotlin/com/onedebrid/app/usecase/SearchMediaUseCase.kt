package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.SearchResult
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val searchRepository: SearchRepository,
    private val profileRepository: ProfileRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(
        query: String,
        profileId: String
    ): RepositoryResult<List<SearchResult>> = withContext(dispatchers.io) {

        try {
            searchRepository.addSearchQuery(profileId, query)
        } catch (e: Exception) {
            // Intentionally ignored.
        }

        // Fetch the profile to check if Adult Content is enabled
        val profileResult = profileRepository.getProfile(profileId)
        val includeAdult = if (profileResult is RepositoryResult.Success) {
            profileResult.data.search.includeAdult
        } else {
            false
        }

        mediaRepository.search(query, profileId, includeAdult)
    }
}