package com.onedebrid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.ui.details.DetailsScreen
import com.onedebrid.app.ui.home.HomeScreen
import com.onedebrid.app.ui.player.PlayerScreen
import com.onedebrid.app.ui.search.SearchScreen
import com.onedebrid.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Settings : Screen("settings")

    data object Details : Screen("details/{mediaType}/{mediaId}?resumePositionMs={resumePositionMs}") {
        fun createRoute(mediaType: MediaType, mediaId: String, resumePositionMs: Long? = null): String {
            val base = "details/${mediaType.name}/$mediaId"
            return if (resumePositionMs != null) "$base?resumePositionMs=$resumePositionMs" else base
        }
    }

    data object Player : Screen("player/{mediaType}/{mediaId}?episodeId={episodeId}&preferredSource={preferredSource}") {
        fun createRoute(
            mediaType: MediaType,
            mediaId: String,
            episodeId: String? = null,
            preferredSource: StreamCandidate? = null
        ): String {
            val base = "player/${mediaType.name}/$mediaId"
            val params = mutableListOf<String>()
            if (!episodeId.isNullOrBlank()) params.add("episodeId=$episodeId")
            if (preferredSource != null) params.add("preferredSource=${preferredSource.title}")
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetails = { mediaType, mediaId, resumeMs ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId, resumeMs))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPlayer = { mediaType, mediaId, episodeId, preferredSource ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = mediaType,
                            mediaId = mediaId,
                            episodeId = episodeId,
                            preferredSource = preferredSource
                        )
                    )
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetails = { mediaId ->
                    navController.navigate(Screen.Details.createRoute(MediaType.MOVIE, mediaId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
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
                onNavigateToPlayer = { mediaType, mediaId, episodeId, preferredSource ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = mediaType,
                            mediaId = mediaId,
                            episodeId = episodeId,
                            preferredSource = preferredSource
                        )
                    )
                }
            )
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
                navArgument("preferredSource") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            PlayerScreen()
        }
    }
}
