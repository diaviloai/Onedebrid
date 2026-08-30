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
     * Resolve a single Episode by its id, given the parent show's mediaId.
     *
     * Added in Session 27 for PlayerViewModel, which resolves an episode
     * from a nav-arg episodeId rather than receiving a full Episode object
     * directly (see PlayerViewModel's doc comment).
     *
     * Implementation note: as of Session 27 there is no per-episode cache
     * entry or provider call — MetadataProvider only fetches full episode
     * lists per show. This is implemented via getEpisodes() plus an
     * in-memory filter to the matching id. That is an internal detail and
     * may change (e.g. once a real per-episode-capable MetadataProvider
     * exists) without affecting callers of this method.
     *
     * Returns AppError.Unknown if getEpisodes() succeeds but no episode
     * in the list matches episodeId — this should be rare (it means a
     * caller passed an episodeId that doesn't belong to mediaId) but is
     * not impossible, e.g. if metadata changed between when the id was
     * captured and when it was looked up again. Flagged as a case worth
     * a proper AppError type in the future error-model review (see open
     * TODO in currentsprint.md re: AppError.ValidationError).
     */
    suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode>

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

    /**
     * Search for media matching the given query.
     *
     * Delegates to the configured SearchProvider(s). With stub providers
     * in place this will always return AllProvidersUnavailable — that is
     * expected until real providers are wired in.
     *
     * Returns a list of SearchResult — a query naturally matches multiple
     * distinct titles, not just one (see SearchProvider.kt's doc comment
     * for the full reasoning).
     *
     * profileId is included so that future provider priority logic can
     * consult the active profile's providerPriorities when selecting
     * which search providers to query.
     */
    suspend fun search(query: String, profileId: String): RepositoryResult<List<SearchResult>>

    /**
     * Find stream candidates for a specific, already-known Media —
     * as opposed to [search], which discovers titles from free text.
     *
     * Added in Session 29 alongside TorrentioSearchProvider.
     * SearchProvider.searchByMedia()'s doc comment has the full reasoning
     * for why this is a separate method from [search] rather than a
     * shared one — in short, ID-based lookup (what Torrentio-style
     * backends actually support) and free-text discovery are genuinely
     * different operations, not two ways of doing the same thing.
     *
     * [episode] should be provided for TV_SHOW media — it supplies the
     * season/episode filters providers need to look up a specific
     * episode's streams. Ignored for MOVIE media.
     *
     * This is the method ResolvePlaybackUseCase's Smart Defaults fallback
     * uses (Session 29) — it already has a resolved Media (and Episode,
     * if applicable) in hand from the PlaybackRequest, so there is
     * nothing to discover, only candidates to look up.
     */
    suspend fun searchStreamsByMedia(
        media: Media,
        episode: Episode? = null
    ): RepositoryResult<List<StreamCandidate>>
}