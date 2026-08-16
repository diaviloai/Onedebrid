package com.onedebrid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onedebrid.app.ui.home.HomeScreen
import com.onedebrid.app.ui.player.PlayerScreen
import com.onedebrid.app.ui.search.SearchScreen
import com.onedebrid.app.ui.settings.SettingsScreen

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
 * Home, Player, Search, and (as of Session 23) Settings are all wired to
 * real screens.
 */
sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Player : Route("player")
    data object Search : Route("search")
    data object Settings : Route("settings")
}

/**
 * The app's single NavHost.
 *
 * Home is wired to the real HomeScreen composable. HomeScreen's own doc
 * comment covers its one deliberate limitation: Continue Watching rows are
 * not yet tappable to resume playback, since WatchedItem carries no full
 * Media object and no Media lookup layer exists yet (currentsprint.md Next
 * Steps item 5).
 *
 * Player is wired for real. It reads its PlaybackRequest from
 * PendingPlaybackHolder rather than from nav arguments — see that file's
 * doc comment for why (nav args can't carry a full PlaybackRequest, and no
 * Media lookup layer exists yet to resolve one from a bare mediaId).
 * onMissingRequest routes back to Home, since a Player entry with nothing
 * pending (e.g. restored back stack after process death, per
 * PendingPlaybackHolder's documented limitation) has nothing to show.
 *
 * Search is wired for real. Navigating to it from Home is a plain forward
 * navigate() with no popUpTo — Search sits on top of Home on the back
 * stack, so a back-press from Search returns to Home normally, matching
 * the flat navigation structure described in UI_UX_Design.md. SearchScreen
 * forwards to Player the same way any future caller will: by populating
 * PendingPlaybackHolder and then navigating to Route.Player.path — see
 * SearchScreen.kt's own doc comment for its two deliberate limitations
 * (TV_SHOW results not yet playable, no manual stream-source picker yet).
 *
 * Settings is wired for real (Session 23), reached the same way as
 * Search — a plain forward navigate() from Home with no popUpTo, so
 * back-press returns to Home normally. SettingsScreen.kt takes no
 * PendingPlaybackHolder or navigation callbacks of its own; it is a leaf
 * destination in this graph (no further forward navigation happens from
 * it yet).
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
            HomeScreen(
                onNavigateToSearch = {
                    navController.navigate(Route.Search.path)
                },
                onNavigateToSettings = {
                    navController.navigate(Route.Settings.path)
                }
            )
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

        composable(Route.Settings.path) {
            SettingsScreen()
        }
    }
}