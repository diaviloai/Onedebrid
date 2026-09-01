package com.onedebrid.app.provider.metadata.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for TMDB API v3 (https://api.themoviedb.org/3/).
 *
 * Authentication (Bearer token) is applied by an OkHttp interceptor
 * (see NetworkModule.kt's TMDB AuthInterceptor), not here — this
 * interface describes the wire contract only.
 *
 * Endpoint shapes verified against TMDB's own current documentation
 * (see TmdbDto.kt's doc comment for the specific sourcing/caveats,
 * especially around append_to_response and imdb_id availability).
 */
interface TmdbApi {

    /**
     * GET /search/multi — free-text search across movies, TV shows, and
     * people in one call. Callers must filter out mediaType == "person"
     * results themselves (TmdbMetadataProvider does this).
     *
     * Does NOT support append_to_response — search results never carry
     * imdb_id. See TmdbDto.kt's doc comment.
     */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String? = null
    ): TmdbSearchResponseDto

    /**
     * GET /movie/{movie_id} — full movie details. imdb_id is present at
     * the top level of the response without needing append_to_response,
     * but external_ids is still requested by default (via
     * [appendToResponse]) for consistency with getTvDetails() and in
     * case TMDB ever changes this.
     */
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
        @Query("language") language: String? = null
    ): TmdbMovieDetailsDto

    /**
     * GET /tv/{tv_id} — full TV series details. Unlike movies, TV
     * details have NO top-level imdb_id — append_to_response=
     * external_ids (the default here) is required to get it at all.
     */
    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
        @Query("language") language: String? = null
    ): TmdbTvDetailsDto

    /**
     * GET /tv/{tv_id}/season/{season_number} — full episode list for one
     * season, in a single call (TMDB does not paginate this endpoint —
     * see TmdbDto.kt's doc comment).
     */
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String? = null
    ): TmdbSeasonDto
}