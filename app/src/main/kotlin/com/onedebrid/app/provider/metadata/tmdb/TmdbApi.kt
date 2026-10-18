package com.onedebrid.app.provider.metadata.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String? = null
    ): TmdbSearchResponseDto

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
        @Query("language") language: String? = null
    ): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("append_to_response") appendToResponse: String? = "external_ids",
        @Query("language") language: String? = null
    ): TmdbTvDetailsDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String? = null
    ): TmdbSeasonDto
}