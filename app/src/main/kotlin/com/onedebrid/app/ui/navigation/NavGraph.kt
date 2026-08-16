package com.onedebrid.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onedebrid.app.ui.player.PlayerScreen
import com.onedebrid.app.ui.search.SearchScreen

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
 * Home, Player, and (as of Session 23) Search are wired to real screens.
 * Settings is still not declared — ProfileViewModel exists but no Settings
 * Composable screen has been built yet. Adding a Route entry with no
 * destination to navigate to would be dead code that implies more is wired
 * up than actually is.
 */
sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Player : Route("player")
    data object Search : Route("search")
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
 * As of Session 23, the Home placeholder has one added button that
 * navigates to Search — a deliberate, minimal decision (agreed with Dia)
 * so Search is actually reachable from the running app, without trying to
 * build out Home's real UI (Continue Watching row, Watchlist row, etc. per
 * UI_UX_Design.md) as part of this navigation-wiring task. This button
 * will be removed once HomeScreen.kt exists with its own real navigation
 * affordances (e.g. a search bar or search icon in Home's own layout).
 *
 * Player is wired for real. It reads its PlaybackRequest from
 * PendingPlaybackHolder rather than from nav arguments — see that file's
 * doc comment for why (nav args can't carry a full PlaybackRequest, and no
 * Media lookup layer exists yet to resolve one from a bare mediaId).
 * onMissingRequest routes back to Home, since a Player entry with nothing
 * pending (e.g. restored back stack after process death, per
 * PendingPlaybackHolder's documented limitation) has nothing to show.
 *
 * Search is wired for real (Session 23). Navigating to it from Home is a
 * plain forward navigate() with no popUpTo — Search sits on top of Home on
 * the back stack, so a back-press from Search returns to Home normally,
 * matching the flat navigation structure described in UI_UX_Design.md.
 * SearchScreen forwards to Player the same way any future caller will:
 * by populating PendingPlaybackHolder and then navigating to
 * Route.Player.path — see SearchScreen.kt's own doc comment for the two
 * deliberate limitations still present in this first version (TV_SHOW
 * results not yet playable, no manual stream-source picker yet).
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
            // trying to match UI_UX_Design.md's Home Hub layout. The
            // "Search" button below is the one deliberate exception,
            // added in Session 23 solely to make Search reachable — see
            // this file's top-level doc comment.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Home — placeholder, HomeScreen.kt not yet built",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { navController.navigate(Route.Search.path) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Search")
                    }
                }
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

        composable(Route.Search.path) {
            SearchScreen(
                pendingPlaybackHolder = pendingPlaybackHolder,
                onNavigateToPlayer = {
                    navController.navigate(Route.Player.path)
                }
            )
        }
    }
}