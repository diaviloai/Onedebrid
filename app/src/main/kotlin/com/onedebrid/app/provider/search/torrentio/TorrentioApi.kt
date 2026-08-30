package com.onedebrid.app.provider.search.torrentio

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for the Torrentio addon API.
 *
 * Torrentio follows the general Stremio addon protocol
 * (https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md):
 * GET /stream/{type}/{id}.json
 *
 * [type] is "movie" or "series".
 * [id] is an IMDb ID for movies (e.g. "tt1234567"), or
 * "{imdbId}:{season}:{episode}" for series (e.g. "tt1234567:1:5").
 *
 * No API key is required for this endpoint — Torrentio only requires a
 * debrid key when asked to resolve a stream itself, which OneDebrid does
 * not use it for (resolution goes through DebridProvider /
 * Real-Debrid directly, per Provider Architecture v0.1). This interface
 * only covers the unauthenticated search/discovery step.
 *
 * Base URL is provided by NetworkModule.kt (https://torrentio.strem.fun/).
 */
interface TorrentioApi {

    @GET("stream/{type}/{id}.json")
    suspend fun getStreams(
        @Path("type") type: String,
        @Path("id") id: String
    ): TorrentioStreamResponseDto
}