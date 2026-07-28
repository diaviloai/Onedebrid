package com.onedebrid.app.provider.search

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.SearchResult

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
     * Searches for stream candidates matching the given query.
     *
     * [query] is a free-text title search.
     * [filters] optionally constrains results by year, type, quality, etc.
     *
     * Returns a [SearchResult] containing ranked [StreamCandidate] objects.
     * An empty result list inside Success is valid — it means this provider
     * found nothing, not that the search failed.
     */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters()
    ): ProviderResult<SearchResult>
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