package com.onedebrid.app.usecase

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {

    operator fun invoke(): Flow<RepositoryResult<UserProfile>> =
        profileRepository.observeActiveProfile()
}