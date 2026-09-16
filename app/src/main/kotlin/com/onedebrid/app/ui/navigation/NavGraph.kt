package com.onedebrid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.ui.details.DetailsScreen
import com.onedebrid.app.ui.home.HomeScreen
import com.onedebrid.app.ui.player.PlayerScreen
import com.onedebrid.app.ui.search.SearchScreen
import com.onedebrid.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Details : Screen("details/{mediaType}/{mediaId}?resumePositionMs={resumePositionMs}") {
        fun createRoute(mediaType: MediaType, mediaId: String, resumePositionMs: Long? = null): String {
            return if (resumePositionMs != null) {
                "details/${mediaType.name.lowercase()}/$mediaId?resumePositionMs=$resumePositionMs"
            } else {
                "details/${mediaType.name.lowercase()}/$mediaId"
            }
        }
    }
    object Player : Screen("player/{mediaType}/{mediaId}?episodeId={episodeId}&streamUrl={streamUrl}") {
        fun createRoute(mediaType: MediaType, mediaId: String, episodeId: String?, streamUrl: String): String {
            return "player/${mediaType.name.lowercase()}/$mediaId?episodeId=${episodeId ?: ""}&streamUrl=$streamUrl"
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
                onNavigateToDetails = { mediaType, mediaId, resumePositionMs ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId, resumePositionMs))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPlayer = { mediaType, mediaId, episodeId, streamUrl ->
                    navController.navigate(Screen.Player.createRoute(mediaType, mediaId, episodeId, streamUrl))
                }
            )
        }

        composable(
            route = Screen.Details.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("resumePositionMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            DetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { mediaType, mediaId, episodeId, candidate ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType,
                            mediaId,
                            episodeId,
                            candidate.title
                        )
                    )
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("episodeId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("streamUrl") { type = NavType.StringType }
            )
        ) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

