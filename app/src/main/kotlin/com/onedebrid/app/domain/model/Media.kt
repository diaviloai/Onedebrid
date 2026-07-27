package com.onedebrid.app.domain.model

/**
 * The canonical representation of playable content within OneDebrid.
 *
 * Media is the shared language between all subsystems. Search results reference it,
 * metadata enriches it, playback consumes it, and Continue Watching tracks it.
 *
 * Fields are nullable where data may not be available immediately. A Media object
 * can be created from minimal search result information and enriched later by the
 * Metadata system. This ensures metadata loading never blocks playback.
 */
data class Media(
    val id: String,
    val title: String,
    val type: MediaType,
    val year: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Float? = null,
    val genres: List<String> = emptyList()
)

/**
 * Distinguishes between content types that have fundamentally different structures.
 *
 * TV_SHOW content has seasons and episodes. MOVIE content does not.
 * Systems that behave differently for each type switch on this value.
 */
enum class MediaType {
    MOVIE,
    TV_SHOW
}