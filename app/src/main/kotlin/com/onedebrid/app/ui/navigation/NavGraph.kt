package com.onedebrid.app.ui.navigation

import android.net.Uri
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    
    // Updated to match DetailsViewModel expectations
    object Details : Screen("details/{mediaId}?mediaType={mediaType}&resumePositionMs={resumePositionMs}") {
        fun createRoute(mediaId: String, mediaType: String = "MOVIE", resumePositionMs: Long = -1L): String {
            return "details/$mediaId?mediaType=$mediaType&resumePositionMs=$resumePositionMs"
        }
    }
    
    // Updated to include preferredSource for the PlayerViewModel
    object Player : Screen("player/{mediaId}?episodeId={episodeId}&resumeMs={resumeMs}&preferredSource={preferredSource}") {
        fun createRoute(args: PlayerNavArgs): String {
            val ep = args.episodeId ?: "none"
            val pos = args.resumeMs ?: -1L
            val src = args.preferredSourceJson ?: ""
            return "player/${args.mediaId}?episodeId=$ep&resumeMs=$pos&preferredSource=$src"
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
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType; defaultValue = "MOVIE" },
                navArgument("resumePositionMs") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            DetailsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { _, mediaId, episodeId, resumeMs, candidate ->
                    // Serialize the selected stream so the Player doesn't have to guess which one to play
                    val candidateJson = Uri.encode(Json.encodeToString(candidate))
                    navController.navigate(
                        Screen.Player.createRoute(
                            PlayerNavArgs(
                                mediaId = mediaId,
                                episodeId = episodeId,
                                resumeMs = resumeMs,
                                preferredSourceJson = candidateJson
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
                navArgument("episodeId") { type = NavType.StringType; defaultValue = "none" },
                navArgument("resumeMs") { type = NavType.LongType; defaultValue = -1L },
                navArgument("preferredSource") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}