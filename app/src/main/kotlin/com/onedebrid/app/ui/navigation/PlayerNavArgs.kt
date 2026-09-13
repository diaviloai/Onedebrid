package com.onedebrid.app.ui.navigation

import com.onedebrid.app.domain.model.StreamCandidate

/**
 * The nav args needed to build a Route.Player destination (Session 27).
 *
 * Carried through HomeViewModel's and DetailsViewModel's navigateToPlayer
 * events so HomeScreen/DetailsScreen/NavGraph don't need to know
 * WatchedItem's or Media's shape — only this. Lives in ui.navigation
 * (alongside Route) rather than ui.home, since it's a nav-layer concept
 * shared by two different ViewModels' packages, not something that
 * belongs to Home specifically.
 *
 * preferredSource (stream-candidate picker feature): set when the user
 * manually picked a StreamCandidate from Details' picker sheet, rather
 * than relying on Smart Defaults. Carried here as a real StreamCandidate,
 * not a pre-serialized String — JSON-encoding is Route.Player.build()'s
 * concern (same layer that already owns the "none"/-1L sentinel encoding
 * for episodeId/resumeMs), so DetailsViewModel/HomeViewModel only ever
 * deal in domain objects, never nav-arg wire format. null (the default,
 * and the only value HomeViewModel ever sends) means "no manual pick —
 * PlayerViewModel should resolve Smart Defaults," unchanged behavior from
 * every session before this one.
 */
data class PlayerNavArgs(
    val mediaId: String,
    val episodeId: String?,
    val resumeMs: Long?,
    val preferredSource: StreamCandidate? = null
)