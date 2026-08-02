package com.onedebrid.app.data.repository

import com.onedebrid.app.data.local.dao.ProfileDao
import com.onedebrid.app.data.local.entity.ProfileEntity
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val dispatchers: CoroutineDispatchers
) : ProfileRepository {

    // --- Observation ---

    override fun observeProfiles(): Flow<List<UserProfile>> =
        profileDao.observeAllProfiles()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeActiveProfile(): Flow<UserProfile> =
        profileDao.observeActiveProfile()
            .filterNotNull()
            .map { it.toDomain() }
            .flowOn(dispatchers.io)

    // --- One-shot reads ---

    override suspend fun getProfile(profileId: String): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            val entity = profileDao.getProfileById(profileId)
            if (entity != null) {
                RepositoryResult.Success(entity.toDomain())
            } else {
                RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        cause = Exception("Profile not found: $profileId")
                    )
                )
            }
        }

    // --- Writes ---

    override suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.insertProfile(ProfileEntity.fromDomain(profile))
            }.fold(
                onSuccess = { RepositoryResult.Success(profile) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    override suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.updateProfile(ProfileEntity.fromDomain(profile))
            }.fold(
                onSuccess = { RepositoryResult.Success(profile) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    override suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.deactivateAllProfiles()
                profileDao.setProfileActive(profileId)
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    override suspend fun deleteProfile(profileId: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            val active = profileDao.getActiveProfile()
            if (active?.id == profileId) {
                return@withContext RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        cause = Exception("Cannot delete the active profile")
                    )
                )
            }
            runCatching {
                profileDao.deleteProfile(profileId)
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }
}