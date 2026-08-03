package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(): Flow<List<UserProfile>>
    fun observeActiveProfile(): Flow<UserProfile>
    suspend fun getProfile(profileId: String): RepositoryResult<UserProfile>
    suspend fun getActiveProfile(): RepositoryResult<UserProfile>
    suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile>
    suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile>
    suspend fun deleteProfile(profileId: String): RepositoryResult<Unit>
    suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit>
}