package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {

    // --- Observation ---

    /**
     * Observes recently played media for a profile, most recent first.
     * Drives the Recently Played row on the home screen.
     *
     * Includes completed items — unlike Continue Watching, Recently Played
     * shows everything that was played regardless of completion status.
     */
    @Query("""
        SELECT * FROM recently_played
        WHERE profileId = :profileId
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
    """)
    fun observeRecentlyPlayed(
        profileId: String,
        limit: Int = 20
    ): Flow<List<RecentlyPlayedEntity>>

    // --- One-shot reads ---

    /**
     * Returns the recently played entry for a specific media item once.
     * Used to check whether an item has been played before, and when.
     */
    @Query("""
        SELECT * FROM recently_played
        WHERE profileId = :profileId AND mediaId = :mediaId
        LIMIT 1
    """)
    suspend fun getEntryForMedia(
        profileId: String,
        mediaId: String
    ): RecentlyPlayedEntity?

    // --- Writes ---

    /**
     * Inserts or replaces a recently played entry.
     * REPLACE on conflict updates lastPlayedAt when the same media is
     * played again, keeping only one row per (profileId, mediaId) pair
     * and moving it back to the top of the list.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: RecentlyPlayedEntity)

    /**
     * Removes a specific media item from recently played.
     * Used if the user explicitly removes an item from their history.
     */
    @Query("""
        DELETE FROM recently_played
        WHERE profileId = :profileId AND mediaId = :mediaId
    """)
    suspend fun removeEntry(profileId: String, mediaId: String)

    /**
     * Clears all recently played entries for a profile.
     * Called from Settings when the user clears their play history.
     */
    @Query("DELETE FROM recently_played WHERE profileId = :profileId")
    suspend fun clearAllForProfile(profileId: String)
}