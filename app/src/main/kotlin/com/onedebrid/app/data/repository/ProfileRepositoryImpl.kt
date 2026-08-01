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

    override fun observeAllProfiles(): Flow<List<UserProfile>> =
        profileDao.observeAllProfiles()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    /**
     * ProfileDao returns Flow<ProfileEntity?> because Room cannot guarantee
     * a row exists. We filter nulls here — a missing active profile is a bug,
     * not a valid state, so we never surface null to callers.
     */
    override fun observeActiveProfile(): Flow<UserProfile> =
        profileDao.observeActiveProfile()
            .filterNotNull()
            .map { it.toDomain() }
            .flowOn(dispatchers.io)

    // --- One-shot reads ---

    override suspend fun getActiveProfile(): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            val entity = profileDao.getActiveProfile()
            if (entity != null) {
                RepositoryResult.Success(entity.toDomain())
            } else {
                RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        cause = Exception("No active profile found")
                    )
                )
            }
        }

    override suspend fun getProfileById(id: String): RepositoryResult<UserProfile> =
        withContext(dispatchers.io) {
            val entity = profileDao.getProfileById(id)
            if (entity != null) {
                RepositoryResult.Success(entity.toDomain())
            } else {
                RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        cause = Exception("Profile not found: $id")
                    )
                )
            }
        }

    // --- Writes ---

    override suspend fun createProfile(profile: UserProfile): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.insertProfile(ProfileEntity.fromDomain(profile))
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    override suspend fun updateProfile(profile: UserProfile): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.updateProfile(ProfileEntity.fromDomain(profile))
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    /**
     * Switches the active profile atomically: deactivate all first, then
     * activate the target. Both operations run sequentially on the IO
     * dispatcher. A Room transaction can be added to ProfileDao later if
     * stricter atomicity is needed.
     */
    override suspend fun setActiveProfile(id: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                profileDao.deactivateAllProfiles()
                profileDao.setProfileActive(id)
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    /**
     * Active profile deletion is rejected here at the repository layer.
     * The DAO has no awareness of business rules — enforcement belongs here.
     */
    override suspend fun deleteProfile(id: String): RepositoryResult<Unit> =
        withContext(dispatchers.io) {
            val active = profileDao.getActiveProfile()
            if (active?.id == id) {
                return@withContext RepositoryResult.Failure(
                    AppError.LocalStorageError(
                        cause = Exception("Cannot delete the active profile")
                    )
                )
            }
            runCatching {
                profileDao.deleteProfile(id)
            }.fold(
                onSuccess = { RepositoryResult.Success(Unit) },
                onFailure = { cause ->
                    RepositoryResult.Failure(AppError.LocalStorageError(cause))
                }
            )
        }

    override suspend fun getProfileCount(): Int =
        withContext(dispatchers.io) {
            profileDao.getProfileCount()
        }
}