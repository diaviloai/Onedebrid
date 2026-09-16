package com.onedebrid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onedebrid.app.ui.details.DetailsScreen
import com.onedebrid.app.ui.home.HomeScreen
import com.onedebrid.app.ui.player.PlayerScreen
import com.onedebrid.app.ui.search.SearchScreen
import com.onedebrid.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Details : Screen("details/{mediaId}") {
        fun createRoute(mediaId: String): String {
            return "details/$mediaId"
        }
    }
    object Player : Screen("player/{mediaId}?episodeId={episodeId}&resumeMs={resumeMs}") {
        fun createRoute(args: PlayerNavArgs): String {
            val ep = args.episodeId ?: ""
            val pos = args.resumeMs ?: -1L
            return "player/${args.mediaId}?episodeId=$ep&resumeMs=$pos"
        }
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetails = { mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPlayer = { navArgs ->
                    navController.navigate(Screen.Player.createRoute(navArgs))
                }
            )
        }

        composable(
            route = Screen.Details.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType }
            )
        ) {
            DetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { _, mediaId, episodeId, candidate ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            PlayerNavArgs(
                                mediaId = mediaId,
                                episodeId = episodeId,
                                resumeMs = null
                            )
                        )
                    )
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetails = { mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("episodeId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("resumeMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
