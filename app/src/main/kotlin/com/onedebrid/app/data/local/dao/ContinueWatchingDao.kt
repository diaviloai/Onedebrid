package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.ContinueWatchingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContinueWatchingDao {

    // --- Observation ---

    /**
     * Observes incomplete items for the given profile, ordered by most
     * recently watched. This is the query that drives the Continue Watching
     * row on the home screen.
     *
     * isCompleted = 0 filters out anything the user has finished.
     * limit allows the UI to cap the list without loading the full table.
     */
    @Query("""
        SELECT * FROM continue_watching
        WHERE profileId = :profileId AND isCompleted = 0
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
    """)
    fun observeContinueWatching(
        profileId: String,
        limit: Int = 20
    ): Flow<List<ContinueWatchingEntity>>

    /**
     * Observes a single Continue Watching entry for a specific media item.
     * Used on the details screen to show resume position and progress.
     * Returns null if no progress exists for this item.
     */
    @Query("""
        SELECT * FROM continue_watching
        WHERE profileId = :profileId AND mediaId = :mediaId
        LIMIT 1
    """)
    fun observeProgressForMedia(
        profileId: String,
        mediaId: String
    ): Flow<ContinueWatchingEntity?>

    // --- One-shot reads ---

    /**
     * Returns the current progress entry for a specific media item once.
     * Used by the playback system on startup to determine resume position.
     */
    @Query("""
        SELECT * FROM continue_watching
        WHERE profileId = :profileId AND mediaId = :mediaId
        LIMIT 1
    """)
    suspend fun getProgressForMedia(
        profileId: String,
        mediaId: String
    ): ContinueWatchingEntity?

    // --- Writes ---

    /**
     * Inserts or replaces a Continue Watching entry.
     *
     * REPLACE handles the upsert case: when the (profileId, mediaId) pair
     * already exists, the old row is replaced with the new one. This means
     * progress saves during playback always use this single function — there
     * is no separate "update progress" path.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entry: ContinueWatchingEntity)

    /**
     * Marks a media item as completed for the given profile.
     * Does not delete the row — the repository uses this record for
     * Recently Played. The Continue Watching query filters it out via
     * isCompleted = 0.
     */
    @Query("""
        UPDATE continue_watching
        SET isCompleted = 1, lastWatchedAt = :completedAt
        WHERE profileId = :profileId AND mediaId = :mediaId
    """)
    suspend fun markAsCompleted(
        profileId: String,
        mediaId: String,
        completedAt: Long
    )

    /**
     * Removes a specific item from Continue Watching entirely.
     * Used when the user explicitly dismisses an item from the list.
     */
    @Query("""
        DELETE FROM continue_watching
        WHERE profileId = :profileId AND mediaId = :mediaId
    """)
    suspend fun removeEntry(profileId: String, mediaId: String)

    /**
     * Removes all Continue Watching entries for a profile.
     * Used when the user clears their watch history.
     * Profile CASCADE handles deletion when the profile itself is deleted —
     * this is for an explicit user-initiated clear within an active profile.
     */
    @Query("DELETE FROM continue_watching WHERE profileId = :profileId")
    suspend fun clearAllForProfile(profileId: String)
}