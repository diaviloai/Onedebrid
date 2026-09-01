package com.onedebrid.app.provider.metadata.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response DTOs for The Movie Database (TMDB) API v3/v4.
 *
 * Kept private to this package per Technical Standards v0.1 — these are
 * network-shape objects, mapped to domain Media/Episode by
 * TmdbMetadataProvider before crossing into the rest of the app.
 *
 * Field shapes verified against TMDB's own current documentation and
 * multiple independent real-world integration examples (not assumed) —
 * see currentsprint.md for sourcing notes. Two shape quirks worth
 * calling out up front:
 *
 * 1. append_to_response (used for external_ids below) is only supported
 *    on the movie/tv/season/episode/person *detail* endpoints — it does
 *    NOT work on /search/multi or any other search/list endpoint. This
 *    is why TmdbSearchResultDto below has no imdbId field at all: TMDB
 *    genuinely cannot provide it at search time. See
 *    MetadataProvider.searchMedia()'s doc comment for how this
 *    constrains callers.
 *
 * 2. Movie details return imdb_id directly at the top level, no append
 *    needed. TV details do NOT — TV requires
 *    append_to_response=external_ids, which nests the id under a
 *    separate externalIds object instead of a top-level field. The two
 *    detail DTOs below are shaped differently on purpose to reflect
 *    this real asymmetry, not for consistency with each other.
 */

// --- Search ---

/**
 * A single entry from GET /3/search/multi.
 *
 * Movie and TV results share this one shape rather than a sealed
 * hierarchy — kotlinx.serialization polymorphism isn't worth the
 * complexity for this few fields. All content fields are nullable;
 * [mediaType] tells the mapper which of the parallel title/date fields
 * to use. "person" results (also returned by /search/multi) are mapped
 * to null and filtered out by the caller — OneDebrid has no use for
 * person results.
 */
@Serializable
data class TmdbSearchResultDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    // Movie fields
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    // TV fields
    val name: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    // Shared fields
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null
)

@Serializable
data class TmdbSearchResponseDto(
    val page: Int? = null,
    val results: List<TmdbSearchResultDto> = emptyList()
)

// --- External IDs (TV only needs this as a separate nested block) ---

@Serializable
data class TmdbExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null
)

// --- Movie details ---

@Serializable
data class TmdbMovieDetailsDto(
    val id: Int,
    val title: String,
    @SerialName("release_date") val releaseDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    val genres: List<TmdbGenreDto>? = null,
    // Present directly on movie details without needing append_to_response.
    @SerialName("imdb_id") val imdbId: String? = null,
    // Present only if append_to_response=external_ids was requested.
    // Redundant with imdbId above for movies in practice — mapper
    // prefers the top-level field, falls back to this if ever needed.
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null
)

// --- TV details ---

@Serializable
data class TmdbTvDetailsDto(
    val id: Int,
    val name: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float? = null,
    val genres: List<TmdbGenreDto>? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    // TV details have NO top-level imdb_id field at all (unlike movies).
    // Only reachable via append_to_response=external_ids.
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null
)

@Serializable
data class TmdbGenreDto(
    val id: Int? = null,
    val name: String? = null
)

// --- TV season / episodes ---

/**
 * Response of GET /3/tv/{tv_id}/season/{season_number}.
 * [episodes] contains every episode in the season in one call — TMDB
 * does not paginate this endpoint.
 */
@Serializable
data class TmdbSeasonDto(
    val id: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList()
)

@Serializable
data class TmdbEpisodeDto(
    val id: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null
)