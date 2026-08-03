package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SwitchProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(profileId: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            when (val current = profileRepository.getActiveProfile()) {
                is RepositoryResult.Success -> {
                    if (current.data.id == profileId) {
                        // Already on this profile — nothing to do
                        return@withContext RepositoryResult.Success(Unit)
                    }
                }
                is RepositoryResult.Failure -> {
                    // No active profile found — not blocking, proceed with switch
                }
            }

            profileRepository.setActiveProfile(profileId)
        }
}