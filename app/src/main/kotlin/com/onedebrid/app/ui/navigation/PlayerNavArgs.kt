package com.onedebrid.app.ui.navigation

/**
 * The nav args needed to build a Route.Player destination (Session 27).
 *
 * Carried through HomeViewModel's and DetailsViewModel's navigateToPlayer
 * events so HomeScreen/DetailsScreen/NavGraph don't need to know
 * WatchedItem's or Media's shape — only this. Lives in ui.navigation
 * (alongside Route) rather than ui.home, since it's a nav-layer concept
 * shared by two different ViewModels' packages, not something that
 * belongs to Home specifically.
 */
data class PlayerNavArgs(
    val mediaId: String,
    val episodeId: String?,
    val resumeMs: Long?
)