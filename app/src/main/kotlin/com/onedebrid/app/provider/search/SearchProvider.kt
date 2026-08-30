package com.onedebrid.app.provider.search

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.StreamCandidate

/**
 * Contract for all search and scraper integrations.
 *
 * Responsible for discovering torrent/magnet candidates matching a
 * given query. Results are unresolved — they become playable only after
 * passing through the debrid layer.
 *
 * Multiple SearchProvider implementations are typically queried in
 * parallel and their results merged. Defined in Provider Architecture v0.1.
 */
interface SearchProvider {

    val id: String
    val displayName: String

    /**
     * Searches for media matching the given free-text query.
     *
     * [query] is a free-text title search.
     * [filters] optionally constrains results by year, type, quality, etc.
     *
     * Returns a list of [SearchResult], each pairing a distinct [Media] match
     * with its own ranked [StreamCandidate] objects. A search naturally
     * produces multiple distinct titles (e.g. querying "the office" should
     * surface both the US and UK versions) — a single SearchResult cannot
     * represent that, which is why this returns a list rather than one
     * SearchResult. This is also what makes Aggregator/Union merging across
     * multiple SearchProviders meaningful (Provider_Architecture.md) — there
     * would be nothing to merge if each provider could only ever return one
     * match.
     *
     * An empty list inside Success is valid — it means this provider found
     * nothing, not that the search failed.
     *
     * Session 29 note: no free-text-capable SearchProvider exists yet.
     * Torrent-indexer-style backends (e.g. Torrentio) do not support
     * free-text search at all — they only return streams for an already-
     * known media identifier. TorrentioSearchProvider implements this
     * method by always returning ProviderError.NotFound, since it
     * genuinely cannot fulfil this contract. Free-text search requires a
     * different kind of provider (e.g. a metadata/catalog search backed
     * by TMDB) that does not exist in this codebase yet — see
     * currentsprint.md Next Steps.
     */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters()
    ): ProviderResult<List<SearchResult>>

    /**
     * Finds stream candidates for a specific, already-known piece of Media.
     *
     * Unlike [search], this does not discover new titles — it looks up
     * candidates for a Media the caller has already resolved (e.g. via a
     * MetadataProvider, or from Continue Watching / Details). This is the
     * shape torrent-indexer-style backends like Torrentio actually
     * support: they key on an external ID (typically IMDb), not free text.
     *
     * [media] must carry a non-null [Media.imdbId] for providers that key
     * on IMDb IDs (e.g. Torrentio). Providers that cannot resolve a given
     * Media (e.g. because imdbId is null) should return
     * [com.onedebrid.app.domain.error.ProviderError.NotFound] rather than
     * throwing.
     *
     * [filters] season/episode fields identify which episode is wanted
     * when [media] is a TV_SHOW. Both should be provided together for
     * TV_SHOW lookups — providers that require them may return NotFound
     * if only one is present.
     *
     * Returns a flat list of [StreamCandidate] — there is exactly one
     * Media in play here (the caller already knows what it is), so there
     * is nothing to disambiguate the way [search] must.
     *
     * An empty list inside Success is valid — it means this provider found
     * nothing for this Media, not that the lookup failed.
     *
     * Added in Session 29 alongside TorrentioSearchProvider — see that
     * class's doc comment and currentsprint.md for the full reasoning
     * behind why this is a separate method from [search] rather than a
     * shared one.
     */
    suspend fun searchByMedia(
        media: Media,
        filters: SearchFilters = SearchFilters()
    ): ProviderResult<List<StreamCandidate>>
}

/**
 * Optional constraints applied to a search request.
 *
 * All fields are nullable. Null means "no constraint on this dimension".
 * Providers that do not support a given filter should ignore it silently.
 */
data class SearchFilters(
    val year: Int? = null,
    val isMovie: Boolean? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val minimumSeeders: Int? = null
)