package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(profile: UserProfile): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            if (profile.name.isBlank()) {
                return@withContext RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        IllegalArgumentException("Profile name must not be blank")
                    )
                )
            }

            profileRepository.updateProfile(profile)
        }
}