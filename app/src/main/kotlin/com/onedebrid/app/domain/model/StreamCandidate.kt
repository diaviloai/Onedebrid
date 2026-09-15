package com.onedebrid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents an unresolved torrent/magnet stream candidate found by a SearchProvider.
 *
 * This candidate is carrying raw metadata (infoHash, seeders, title, quality)
 * and must be resolved through a DebridProvider to become a playable StreamSource.
 */
@Serializable
data class StreamCandidate(
    val id: String,
    val mediaId: String,
    val title: String,
    val infoHash: String,
    val fileIndex: Int? = null,
    val quality: VideoQuality = VideoQuality.UNKNOWN,
    val seeders: Int = 0,
    val sizeBytes: Long = 0L,
    val sourceProvider: String
)
