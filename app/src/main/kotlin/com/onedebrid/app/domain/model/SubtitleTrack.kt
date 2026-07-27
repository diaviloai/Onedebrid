package com.onedebrid.app.domain.model

/**
 * Represents a subtitle track available for a piece of media.
 *
 * SubtitleTrack is what the Subtitle system produces after searching an external
 * provider such as OpenSubtitles. It is what the Playback system attaches to a
 * stream via Media3.
 *
 * A single Media item may have many SubtitleTracks available across different
 * languages, formats, and sources. The Subtitle system selects automatically
 * based on profile preferences, but the user may override this selection.
 */
data class SubtitleTrack(
    val id: String,
    val mediaId: String,
    val language: String,
    val languageCode: String,
    val format: SubtitleFormat,
    val url: String,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val releaseName: String? = null,
    val uploadedBy: String? = null
)

/**
 * The file format of a subtitle track.
 *
 * Media3/ExoPlayer supports all three natively. Format determines how the
 * Playback system registers the track with the player.
 */
enum class SubtitleFormat {
    SRT,
    VTT,
    ASS
}