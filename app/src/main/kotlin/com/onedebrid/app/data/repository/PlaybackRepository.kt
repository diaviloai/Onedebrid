package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * Repository for playback history and progress tracking.
 *
 * Owned by the Playback System. Handles Continue Watching,
 * per-item progress, and recently played history.
 */
interface PlaybackRepository {

    // --- Continue Watching ---

    /**
     * Observe the current Continue Watching list.
     *
     * Emits a new list whenever any item's progress changes.
     * Items are ordered by most recently watched first.
     * Only includes items with meaningful progress that are not
     * yet complete.
     */
    fun observeContinueWatching(): Flow<List<Media>>

    /**
     * Remove a specific item from Continue Watching.
     *
     * Called when the user explicitly dismisses an item or when
     * playback reaches completion.
     */
    suspend fun removeFromContinueWatching(mediaId: String)

    // --- Playback Progress ---

    /**
     * Save the current playback position for a media item.
     *
     * Called periodically during playback. Must be fast — this runs
     * while the player is active.
     *
     * [episodeId] is null for movies. For TV episodes, both
     * [mediaId] (the show) and [episodeId] are required to
     * unambiguously identify what was watched.
     *
     * [positionMs] is the current position in milliseconds.
     * [durationMs] is the total duration in milliseconds, used to
     * calculate completion percentage.
     */
    suspend fun saveProgress(
        mediaId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long
    )

    /**
     * Retrieve the last saved playback position for a media item.
     *
     * Returns null if no progress has been saved yet.
     * [episodeId] is null for movies.
     */
    suspend fun getProgress(
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Long?>

    // --- Recently Played ---

    /**
     * Observe the recently played list.
     *
     * Emits a new list whenever an item is added.
     * Ordered by most recently played first.
     * Includes all played items, including completed ones.
     */
    fun observeRecentlyPlayed(): Flow<List<Media>>

    /**
     * Record that a media item was played.
     *
     * Called when playback starts. Distinct from saveProgress —
     * this just records that the item was opened, regardless of
     * how far the user got.
     */
    suspend fun recordPlayed(mediaId: String)

    /**
     * Clear all playback history.
     *
     * User-initiated. Clears both Continue Watching and Recently Played.
     */
    suspend fun clearHistory()
}