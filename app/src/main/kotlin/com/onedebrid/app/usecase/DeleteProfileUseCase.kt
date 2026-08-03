package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(profileId: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            when (val current = profileRepository.getActiveProfile()) {
                is RepositoryResult.Success -> {
                    if (current.data.id == profileId) {
                        return@withContext RepositoryResult.Failure(
                            AppError.LocalStorageError(
                                IllegalStateException("Cannot delete the active profile")
                            )
                        )
                    }
                }
                is RepositoryResult.Failure -> {
                    // No active profile readable — proceed, repository will handle it
                }
            }

            profileRepository.deleteProfile(profileId)
        }
}