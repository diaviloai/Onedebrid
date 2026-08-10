package com.onedebrid.app.ui.navigation

import com.onedebrid.app.domain.model.PlaybackRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a PlaybackRequest across the navigation boundary into the Player
 * screen.
 *
 * Why this exists:
 * Jetpack Navigation Compose route arguments can only be primitives/strings
 * — a full PlaybackRequest (which embeds a Media and optional Episode
 * object) cannot be passed as a nav arg. There is also no Media cache or
 * lookup layer yet (MediaRepository has no getById(mediaId) equivalent, and
 * MetadataProvider is an unimplemented stub — see currentsprint.md), so the
 * nav graph cannot pass a bare mediaId and have PlayerScreen look the rest
 * up. This holder is the deliberate alternative: whichever screen initiates
 * playback already holds the full Media/Episode objects in memory (e.g. a
 * search result the user just tapped), so it sets the request here
 * immediately before calling navController.navigate(), and PlayerScreen
 * reads it back on entry.
 *
 * Read-and-clear:
 * consume() returns the current value and clears it in the same call. This
 * is deliberate, not incidental — if it merely returned the value, a second
 * unrelated navigation to the player route later in the same process
 * lifetime (e.g. after the holder was set once and Player was left via back
 * gesture without a new request being set) would silently replay the old
 * request instead of correctly finding nothing.
 *
 * Known limitation — flagged, not silently accepted:
 * This holder does not survive process death. If Android kills the process
 * while the user is on the Player screen and the system later restores the
 * back stack (e.g. via "recent apps"), the nav arg says "show the player"
 * but this holder will be empty, because it is a plain in-memory singleton
 * with no SavedStateHandle or persistence behind it. PlayerScreen must
 * treat a null consume() result as a real case to handle (e.g. navigate
 * back to the start destination) rather than assuming it is always
 * populated. Building a version of this that survives process death would
 * require either a Media cache/lookup layer (see Option 1 discussed this
 * session) or serializing PlaybackRequest through SavedStateHandle, neither
 * of which exists yet. Deferred deliberately rather than built partially.
 */
@Singleton
class PendingPlaybackHolder @Inject constructor() {

    private var pending: PendingPlayback? = null

    /**
     * Set by the calling screen immediately before navigating to the
     * Player route.
     */
    fun set(request: PlaybackRequest, profileId: String) {
        pending = PendingPlayback(request, profileId)
    }

    /**
     * Read by PlayerScreen on entry. Returns null if nothing is pending —
     * either because set() was never called, or because a prior consume()
     * already claimed it. Clears the held value as part of reading it.
     */
    fun consume(): PendingPlayback? {
        val result = pending
        pending = null
        return result
    }
}

/**
 * The pair PlayerScreen needs to call PlayerViewModel.play(request, profileId).
 * Bundled together so PendingPlaybackHolder has a single value to hold and
 * clear atomically rather than two separate nullable fields that could get
 * out of sync.
 */
data class PendingPlayback(
    val request: PlaybackRequest,
    val profileId: String
)