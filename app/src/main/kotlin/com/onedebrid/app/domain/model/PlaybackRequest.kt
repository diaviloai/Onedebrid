package com.onedebrid.app.domain.model

/**
 * Represents a request to begin playback of a piece of media.
 *
 * PlaybackRequest is the input to the Playback system. It carries everything
 * the system needs to resolve and start a stream — the content to play, the
 * specific episode if applicable, and optionally a pre-selected stream source
 * if the user chose one manually rather than accepting Smart Defaults.
 *
 * ViewModels construct PlaybackRequests and pass them to the ResolvePlayback
 * use case. The Playback system takes it from there.
 *
 * PlaybackRequest is intentionally minimal. It describes intent, not outcome.
 * The Playback system is responsible for resolving that intent into an active
 * PlaybackSession.
 */
data class PlaybackRequest(
    val media: Media,
    val episode: Episode? = null,
    val preferredSource: StreamCandidate? = null,
    val resumePositionMs: Long? = null
)