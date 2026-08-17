package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.WatchedItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository for playback history and progress tracking.
 *
 * Owned by the Playback System. Handles Continue Watching,
 * per-item progress, and recently played history.
 *
 * Returns WatchedItem rather than Media — the repository only holds what
 * the local database knows (IDs, progress, timestamps). Callers are
 * responsible for fetching full Media metadata using the mediaId.
 */
interface PlaybackRepository {

    // --- Continue Watching ---

    /**
     * Observe the current Continue Watching list.
     *
     * Emits a new list whenever any item's progress changes.
     * Items are ordered by most recently watched first.
     * Only includes items with meaningful progress that are not yet complete.
     */
    fun observeContinueWatching(profileId: String): Flow<List<WatchedItem>>

    /**
     * Remove a specific item from Continue Watching.
     *
     * Called when the user explicitly dismisses an item or when
     * playback reaches completion.
     */
    suspend fun removeFromContinueWatching(profileId: String, mediaId: String)

    // --- Playback Progress ---

    /**
     * Save the current playback position for a media item.
     *
     * Called periodically during playback. Must be fast — this runs
     * while the player is active.
     *
     * [episodeId] is null for movies. For TV episodes, both
     * [mediaId] (the show) and [episodeId] are required to
     * unambiguously identify what was watched. [seasonNumber] and
     * [episodeNumber] are likewise null for movies and non-null for TV
     * episodes — they exist purely for display (Continue Watching rows
     * showing "S2 E4" rather than a raw episodeId) and carry no lookup
     * meaning of their own.
     *
     * [positionMs] is the current position in milliseconds.
     * [durationMs] is the total duration in milliseconds, used to
     * calculate completion percentage.
     */
    suspend fun saveProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long
    )

    /**
     * Retrieve the last saved playback position for a media item.
     *
     * Returns null inside Success if no progress has been saved yet.
     * [episodeId] is null for movies.
     */
    suspend fun getProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Long?>

    /**
     * Mark a media item as completed in Continue Watching.
     *
     * Called when playback reaches the completion threshold (typically ~90%).
     */
    suspend fun markAsCompleted(profileId: String, mediaId: String)

    // --- Recently Played ---

    /**
     * Observe the recently played list for a profile.
     *
     * Emits a new list whenever an item is added.
     * Ordered by most recently played first.
     * Includes all played items, including completed ones.
     */
    fun observeRecentlyPlayed(profileId: String): Flow<List<WatchedItem>>

    /**
     * Record that a media item was played.
     *
     * Called when playback starts. Distinct from saveProgress —
     * this records that the item was opened regardless of how far
     * the user got.
     *
     * For TV episodes, episodeId, seasonNumber, and episodeNumber
     * identify which episode was opened.
     */
    suspend fun recordPlayed(
        profileId: String,
        mediaId: String,
        episodeId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    )

    /**
     * Clear all playback history for a profile.
     *
     * User-initiated. Clears both Continue Watching and Recently Played.
     */
    suspend fun clearHistory(profileId: String)
}