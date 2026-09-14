package com.onedebrid/app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onedebrid/app.domain.model.MediaType
import com.onedebrid/app.ui.details.DetailsScreen
import com.onedebrid/app.ui.home.HomeScreen
import com.onedebrid/app.ui.player.PlayerScreen
import com.onedebrid/app.ui.search.SearchScreen
import com.onedebrid/app.ui.settings.SettingsScreen

sealed class Route(val route: String) {
    data object Home : Route("home")
    data object Search : Route("search")
    data object Settings : Route("settings")

    data object Details : Route("details/{mediaType}/{mediaId}?resumePositionMs={resumePositionMs}") {
        fun build(mediaType: MediaType, mediaId: String, resumePositionMs: Long? = null): String {
            val base = "details/${mediaType.name.lowercase()}/$mediaId"
            return if (resumePositionMs != null && resumePositionMs > 0L) {
                "$base?resumePositionMs=$resumePositionMs"
            } else {
                base
            }
        }
    }

    data object Player : Route("player/{mediaType}/{mediaId}/{episodeId}?preferredSource={preferredSource}") {
        fun build(
            mediaType: MediaType,
            mediaId: String,
            episodeId: String? = null,
            preferredSourceJsonEncoded: String = ""
        ): String {
            val ep = episodeId ?: "none"
            val source = preferredSourceJsonEncoded.ifEmpty { "" }
            return "player/${mediaType.name.lowercase()}/$mediaId/$ep?preferredSource=$source"
        }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
        modifier = modifier
    ) {
        composable(Route.Home.route) {
            HomeScreen(
                onNavigateToDetails = { mediaType, mediaId, resumePositionMs ->
                    navController.navigate(Route.Details.build(mediaType, mediaId, resumePositionMs))
                },
                onNavigateToSearch = {
                    navController.navigate(Route.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Route.Settings.route)
                },
                onNavigateToPlayer = { mediaType, mediaId, episodeId, preferredSource ->
                    navController.navigate(Route.Player.build(mediaType, mediaId, episodeId, preferredSource))
                }
            )
        }

        composable(Route.Search.route) {
            SearchScreen(
                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Route.Details.build(mediaType, mediaId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Route.Settings.route) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Route.Details.route,
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
                onNavigateToPlayer = { mediaType, mediaId, episodeId, preferredSource ->
                    navController.navigate(Route.Player.build(mediaType, mediaId, episodeId, preferredSource))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Route.Player.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("episodeId") { type = NavType.StringType },
                navArgument("preferredSource") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            PlayerScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
