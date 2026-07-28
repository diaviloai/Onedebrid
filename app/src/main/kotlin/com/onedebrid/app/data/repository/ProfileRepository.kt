package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user profile management.
 *
 * Owned by the Profile System. Handles creation, retrieval, update,
 * and deletion of profiles, plus tracking the active profile.
 *
 * TODO: UserProfile.kt is missing providerPriorities: Map<String, List<String>>
 *   where the key is provider type ("debrid", "metadata", "subtitle") and
 *   the value is an ordered list of provider IDs. Add this field to
 *   UserProfile during the Room entity session.
 */
interface ProfileRepository {

    // --- Profile CRUD ---

    /**
     * Observe all stored profiles.
     *
     * Emits a new list whenever a profile is created, updated, or deleted.
     * Ordered by creation date, oldest first.
     */
    fun observeProfiles(): Flow<List<UserProfile>>

    /**
     * Retrieve a single profile by ID.
     *
     * Returns Failure if no profile with that ID exists.
     */
    suspend fun getProfile(profileId: String): RepositoryResult<UserProfile>

    /**
     * Create a new profile and persist it.
     *
     * The profile's ID should be generated before calling this.
     * Returns the created profile on success.
     */
    suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile>

    /**
     * Replace an existing profile with updated data.
     *
     * The profile must already exist. Returns Failure if the ID
     * is not found.
     */
    suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile>

    /**
     * Delete a profile by ID.
     *
     * Cannot delete the active profile. The caller must switch to a
     * different profile first. Returns Failure if the profile is
     * currently active or does not exist.
     */
    suspend fun deleteProfile(profileId: String): RepositoryResult<Unit>

    // --- Active Profile ---

    /**
     * Observe the currently active profile.
     *
     * Emits the active profile whenever it changes, including on
     * initial load. Should never emit null after initial setup —
     * the app always has at least one profile.
     */
    fun observeActiveProfile(): Flow<UserProfile>

    /**
     * Set the active profile by ID.
     *
     * The profile must already exist. The change is persisted so
     * it survives app restarts.
     */
    suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit>
}