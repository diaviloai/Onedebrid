package com.onedebrid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a potential stream found by a SearchProvider (like Torrentio).
 * This is passed to a DebridProvider (like Real-Debrid) to be resolved into
 * an actual playable StreamSource.
 */
@Serializable
data class StreamCandidate(
    val title: String,
    val hash: String,
    val magnetUrl: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int = 0,
    val quality: VideoQuality
)