package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.onedebrid.app.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    // --- Observation ---

    /**
     * Observes all profiles. Emits a new list whenever any profile changes.
     * Used by the profile picker UI.
     */
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAllProfiles(): Flow<List<ProfileEntity>>

    /**
     * Observes the currently active profile.
     * Returns Flow<ProfileEntity?> here — the repository layer is responsible
     * for asserting non-nullability and throwing if no active profile exists,
     * since that represents a bug rather than a valid state.
     */
    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<ProfileEntity?>

    // --- One-shot reads ---

    /**
     * Returns the active profile once, without ongoing observation.
     * Used during initialization and profile-switch transactions.
     */
    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProfileEntity?

    /**
     * Returns a single profile by ID.
     * Returns null if the ID does not exist.
     */
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: String): ProfileEntity?

    // --- Writes ---

    /**
     * Inserts a new profile. Replaces on conflict (same primary key).
     * REPLACE strategy covers the upsert case without needing a separate
     * update path for profile creation vs. edit.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    /**
     * Updates an existing profile. Used for preference changes.
     * Room matches by primary key automatically.
     */
    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    /**
     * Deactivates all profiles by setting isActive = 0.
     * Called as the first step of a profile-switch transaction in the
     * repository. Never call this in isolation — always follow with
     * setProfileActive().
     */
    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    /**
     * Sets a specific profile as active.
     * Called as the second step of a profile-switch transaction.
     */
    @Query("UPDATE profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun setProfileActive(profileId: String)

    /**
     * Deletes a profile by ID.
     * The repository is responsible for rejecting deletion of the active
     * profile before calling this. The DAO does not enforce that rule.
     */
    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    /**
     * Returns the total number of profiles.
     * Used by the repository to enforce the one-profile minimum after deletion.
     */
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}