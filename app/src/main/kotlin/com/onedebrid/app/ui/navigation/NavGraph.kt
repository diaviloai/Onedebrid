package com.onedebrid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onedebrid.app.ui.details.DetailsScreen
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
    data object Search : Route("search")
    data object Settings : Route("settings")

    /**
     * Details takes a mediaId nav argument (Session 26) — the first route
     * in this graph to carry one. path is the pattern registered with
     * NavHost ("details/{mediaId}"); [build] produces the concrete route
     * string a caller navigates to for a specific mediaId. A plain string
     * argument, not a full Media, since nav args can't carry domain
     * objects — DetailsViewModel re-fetches the full Media itself via
     * GetMediaByIdUseCase.
     */
    data object Details : Route("details/{mediaId}") {
        fun build(mediaId: String): String = "details/$mediaId"
    }

    /**
     * Player takes mediaId, an optional episodeId, and an optional
     * resumeMs, all as nav args (Session 27) — replacing
     * PendingPlaybackHolder, which was an in-memory singleton that did not
     * survive process death (see that file's former doc comment, and
     * currentsprint.md's Session 27 notes for the full before/after
     * reasoning).
     *
     * episodeId is a query-style optional argument (defaultValue = "none")
     * rather than a required path segment, since a movie has no episode.
     * NavType has no nullable-String-with-null-default support in the
     * simple navArgument {} builder used here in a way that round-trips
     * cleanly through SavedStateHandle, so the sentinel string "none" is
     * used instead and mapped back to null in PlayerViewModel. This
     * mirrors how resumeMs (a Long) uses -1L as its "no value" sentinel
     * for the same underlying reason — NavType.LongType has no nullable
     * variant either. Both sentinels are documented again at the point
     * PlayerViewModel reads them, since that's where the mapping back to
     * null actually happens.
     *
     * build() takes nullable episodeId/resumeMs directly so call sites
     * never need to know about the sentinel values themselves — only this
     * file and PlayerViewModel's SavedStateHandle-reading code do.
     */
    data object Player : Route("player/{mediaId}?episodeId={episodeId}&resumeMs={resumeMs}") {
        fun build(mediaId: String, episodeId: String? = null, resumeMs: Long? = null): String {
            val episodeArg = episodeId ?: "none"
            val resumeArg = resumeMs ?: -1L
            return "player/$mediaId?episodeId=$episodeArg&resumeMs=$resumeArg"
        }
    }
}

/**
 * The app's single NavHost.
 *
 * Home is wired to the real HomeScreen composable. As of Session 25,
 * Continue Watching rows are tappable to resume playback. As of Session
 * 27, tapping a row navigates directly to Player via nav args
 * (Route.Player.build()) rather than resolving a full Media first and
 * populating PendingPlaybackHolder — see HomeScreen.kt/HomeViewModel.kt's
 * own doc comments for what changed and why (PendingPlaybackHolder no
 * longer exists in this codebase as of Session 27).
 *
 * Player is wired for real. As of Session 27 it reads mediaId/episodeId/
 * resumeMs from nav arguments via SavedStateHandle, and resolves its own
 * Media/Episode/active-profile from there — see PlayerViewModel.kt's doc
 * comment for the full resolve flow this replaced (PendingPlaybackHolder).
 * There is no onMissingRequest case anymore: a mediaId is always present
 * (it's a required path segment, not optional), so there is always
 * something for PlayerViewModel to attempt to resolve, even if that
 * resolution can itself fail (surfaced as an error state on the Player
 * screen itself, same as any other resolution failure).
 *
 * Search is wired for real. Navigating to it from Home is a plain forward
 * navigate() with no popUpTo — Search sits on top of Home on the back
 * stack, so a back-press from Search returns to Home normally, matching
 * the flat navigation structure described in UI_UX_Design.md. Tapping any
 * search result (movie or TV show) navigates to Details.
 *
 * Settings is wired for real (Session 23), reached the same way as
 * Search — a plain forward navigate() from Home with no popUpTo, so
 * back-press returns to Home normally. SettingsScreen.kt takes no
 * navigation callbacks of its own; it is a leaf destination in this graph.
 *
 * Details is wired for real (Session 26), reached only from Search today.
 * Takes a mediaId nav argument. Renders movie details with a Play action,
 * or a TV show's episode list. As of Session 27, both actions navigate
 * directly to Player via nav args, same as Home — see
 * DetailsScreen.kt/DetailsViewModel.kt's own doc comments, including why
 * Continue Watching's tap-to-resume flow is deliberately NOT routed
 * through here (resume-position preservation). Back-press returns to
 * Search normally (popBackStack(), no special popUpTo needed since
 * Details never becomes a start destination).
 */
@Composable
fun NavGraph(
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
                },
                onNavigateToPlayer = { mediaId, episodeId, resumeMs ->
                    navController.navigate(Route.Player.build(mediaId, episodeId, resumeMs))
                }
            )
        }

        composable(
            route = Route.Player.path,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("episodeId") {
                    type = NavType.StringType
                    defaultValue = "none"
                },
                navArgument("resumeMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            PlayerScreen()
        }

        composable(Route.Search.path) {
            SearchScreen(
                onNavigateToDetails = { mediaId ->
                    navController.navigate(Route.Details.build(mediaId))
                }
            )
        }

        composable(Route.Settings.path) {
            SettingsScreen()
        }

        composable(
            route = Route.Details.path,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) {
            DetailsScreen(
                onNavigateToPlayer = { mediaId, episodeId, resumeMs ->
                    navController.navigate(Route.Player.build(mediaId, episodeId, resumeMs))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}