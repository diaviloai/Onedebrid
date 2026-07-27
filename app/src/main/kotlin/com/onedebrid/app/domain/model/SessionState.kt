package com.onedebrid.app.domain.model

/**
 * Represents the active state of the current application session.
 *
 * SessionState is the temporary counterpart to UserProfile. Where UserProfile
 * stores what a user permanently prefers, SessionState stores what is happening
 * right now.
 *
 * SessionState is never persisted to the database. If the application is killed
 * and relaunched, session state is rebuilt from scratch. The one exception is
 * playback progress, which is persisted separately by the Playback system via
 * Continue Watching.
 *
 * The Session Coordinator owns and manages this object.
 */
data class SessionState(
    val activeProfile: UserProfile,
    val playback: PlaybackSession? = null,
    val search: SearchSession? = null
)

/**
 * Represents an active or paused playback session.
 *
 * media: The content currently loaded in the player.
 * episode: The specific episode, if the content is a TV show. Null for movies.
 * streamSource: The resolved stream currently being played.
 * positionMs: Current playback position in milliseconds.
 * state: The current lifecycle state of the player.
 */
data class PlaybackSession(
    val media: Media,
    val episode: Episode? = null,
    val streamSource: StreamSource,
    val positionMs: Long = 0L,
    val state: PlaybackState = PlaybackState.IDLE
)

/**
 * Represents an active search within the current session.
 *
 * query: The current search string.
 * results: Results returned so far. Empty until the first response arrives.
 * isLoading: Whether a search is currently in progress.
 */
data class SearchSession(
    val query: String = "",
    val results: List<Media> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * The lifecycle state of the media player.
 *
 * IDLE: Player exists but no media is loaded.
 * BUFFERING: Media is loaded but waiting on data.
 * PLAYING: Media is actively playing.
 * PAUSED: Playback is paused by the user.
 * ENDED: Playback reached the end of the stream.
 * ERROR: Player encountered an unrecoverable error.
 */
enum class PlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}