package com.onedebrid.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onedebrid.app.ui.player.PlayerScreen

/**
 * Route identifiers for the app's navigation graph.
 *
 * A sealed object (not raw string route literals scattered across call
 * sites) so every navigate() call and every composable() destination
 * references the same constant — a typo in a raw string route wouldn't be
 * caught until runtime, a typo referencing Route.Player.path would be
 * caught by the compiler. Matches the project's general preference for
 * sealed types over stringly-typed state (see ScreenUiState in
 * Technical_standards.md).
 *
 * Only Home and Player are wired to real screens right now — see the
 * NavGraph doc comment below for what's still missing and why.
 */
sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Player : Route("player")
    // Search and Settings routes are intentionally not declared yet.
    // SearchViewModel and ProfileViewModel exist, but SearchScreen.kt and
    // a Settings screen do not — see currentsprint.md. Adding a Route
    // entry with no destination to navigate to would be dead code that
    // implies more is wired up than actually is.
}

/**
 * The app's single NavHost.
 *
 * Home is the start destination, but HomeScreen.kt does not exist yet
 * (HomeViewModel does, but no Composable screen has been built — see
 * currentsprint.md). Rather than block this navigation graph on building
 * HomeScreen — a separate, larger piece of work — the Home destination
 * below is a deliberately minimal inline placeholder, clearly marked, that
 * exists only so the graph has a valid, buildable start destination.
 * Replacing it with the real HomeScreen composable is a TODO for a future
 * session and does not require any change to this file's structure — only
 * swapping the placeholder composable() body for a call to the real
 * HomeScreen.
 *
 * Player is wired for real. It reads its PlaybackRequest from
 * PendingPlaybackHolder rather than from nav arguments — see that file's
 * doc comment for why (nav args can't carry a full PlaybackRequest, and no
 * Media lookup layer exists yet to resolve one from a bare mediaId).
 * onMissingRequest routes back to Home, since a Player entry with nothing
 * pending (e.g. restored back stack after process death, per
 * PendingPlaybackHolder's documented limitation) has nothing to show.
 */
@Composable
fun NavGraph(
    pendingPlaybackHolder: PendingPlaybackHolder,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.path,
        modifier = modifier
    ) {
        composable(Route.Home.path) {
            // TODO(next session): replace with the real HomeScreen
            // composable once it exists. Kept intentionally minimal — this
            // is a placeholder, not a first draft of Home's UI, so it isn't
            // trying to match UI_UX_Design.md's Home Hub layout.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Home — placeholder, HomeScreen.kt not yet built",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        composable(Route.Player.path) {
            PlayerScreen(
                pendingPlaybackHolder = pendingPlaybackHolder,
                onMissingRequest = {
                    navController.navigate(Route.Home.path) {
                        // Clears Player off the back stack so a subsequent
                        // back-press from Home doesn't return to the same
                        // empty Player entry. popUpTo(startDestination)
                        // with inclusive = true also handles the case where
                        // Home itself is what's being popped through, since
                        // Home is the start destination and there's nothing
                        // before it to worry about losing.
                        popUpTo(Route.Home.path) { inclusive = true }
                    }
                }
            )
        }
    }
}