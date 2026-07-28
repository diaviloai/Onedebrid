package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.StreamCandidate

/**
 * Repository for media content and stream resolution.
 *
 * Abstracts metadata providers (e.g. TMDB) and debrid providers
 * (e.g. Real-Debrid) behind a single interface. Callers never know
 * whether data came from the network, a local cache, or the database.
 */
interface MediaRepository {

    /**
     * Fetch full metadata for a single media item by its ID.
     *
     * The ID is the application's internal media ID, not a provider-specific
     * ID. The repository is responsible for resolving any external ID mapping
     * internally (e.g. IMDB ID → TMDB ID).
     */
    suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media>

    /**
     * Fetch the episode list for a TV series.
     *
     * Returns all episodes across all seasons. Callers filter by season
     * if needed. Returns an empty list (not a failure) if the media item
     * is a movie or has no episode data yet.
     */
    suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>>

    /**
     * Check whether a stream candidate is cached on the debrid service
     * and resolve it to a direct playable URL.
     *
     * A StreamCandidate is an unresolved torrent or magnet link from search.
     * A StreamSource is a confirmed, playable direct URL.
     * These are intentionally separate types — a candidate that has not been
     * resolved must never reach the player.
     */
    suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource>

    /**
     * Check cache status for multiple candidates at once.
     *
     * Batching is important here — checking candidates one at a time would
     * result in one network request per result, which is too slow for a
     * search results screen. The debrid API supports bulk hash checks.
     *
     * Returns a map of candidate ID to whether it is cached.
     * Does not resolve streams — use resolveStream() for that.
     */
    suspend fun checkCacheStatus(candidates: List<StreamCandidate>): RepositoryResult<Map<String, Boolean>>
}