package com.onedebrid.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a resolved, playable stream for a piece of media.
 *
 * StreamSource is what the Debrid system produces after resolving a torrent or
 * magnet link through Real-Debrid. It is what the Playback system consumes.
 *
 * A single Media item may have multiple StreamSources available, differing by
 * quality, file size, or source. The user may select one manually, or Smart
 * Defaults will select the best match automatically based on profile preferences.
 */
data class StreamSource(
    val id: String,
    val mediaId: String,
    val url: String,
    val quality: VideoQuality,
    val fileSizeBytes: Long? = null,
    val fileName: String? = null,
    val codec: String? = null,
    val audioInfo: String? = null,
    val isCached: Boolean = false
)

/**
 * Represents the video quality of a stream source.
 *
 * UNKNOWN is used when quality cannot be determined from available metadata.
 * This prevents null handling at call sites while still representing uncertainty.
 *
 * @Serializable added (stream-candidate picker feature) so StreamCandidate,
 * which carries a VideoQuality field, can itself be @Serializable — needed
 * to encode a manually-picked StreamCandidate as a nav argument between
 * Details and Player (nav args can only carry primitives/strings, so the
 * candidate travels as a JSON string). StreamSource itself is NOT made
 * @Serializable here — it never crosses a nav-arg boundary, only
 * StreamCandidate does (see PlaybackRequest.preferredSource).
 */
@Serializable
enum class VideoQuality {
    SD,
    HD_720,
    HD_1080,
    UHD_4K,
    UNKNOWN
}