package com.onedebrid.app.domain.model

/**
 * A lightweight record of a profile's interaction with a media item.
 *
 * WatchedItem contains only what the local database knows — the media ID,
 * optional episode context, and playback progress if available. It does not
 * contain full Media metadata (title, artwork, etc.), which must be fetched
 * separately from the metadata provider or cache using [mediaId].
 *
 * Used by:
 * - Continue Watching: positionMs, durationMs, and isCompleted are populated
 * - Recently Played: positionMs, durationMs, and isCompleted are null
 *
 * For movies, episodeId, seasonNumber, and episodeNumber are all null.
 * For TV episodes, all three are non-null.
 */
data class WatchedItem(
    val mediaId: String,

    // Episode context — null for movies, non-null for TV episodes
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    // Populated for Continue Watching entries, null for Recently Played
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val isCompleted: Boolean? = null,

    // Most recent interaction timestamp
    val lastInteractedAt: Long
)