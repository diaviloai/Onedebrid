package com.onedebrid.app.provider.metadata

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media

/**
 * Contract for all metadata service integrations (e.g. TMDB, TVDB).
 *
 * Responsible for enriching Media with titles, artwork, descriptions,
 * ratings, and episode structure. Metadata is strictly non-blocking —
 * playback must never wait for metadata to complete.
 *
 * Defined in Provider Architecture v0.1.
 */
interface MetadataProvider {

    val id: String
    val displayName: String

    /**
     * Fetches full metadata for a piece of media identified by an
     * external ID (e.g. an IMDb ID or TMDB ID).
     *
     * [externalId] is a provider-agnostic string identifier.
     * [idType] specifies which ID scheme is being used.
     *
     * Returns an enriched [Media] object. May return [ProviderError.NotFound]
     * if the ID is not recognised by this provider.
     */
    suspend fun fetchMediaDetails(
        externalId: String,
        idType: ExternalIdType
    ): ProviderResult<Media>

    /**
     * Fetches the full episode list for a TV series.
     *
     * [externalId] identifies the parent series.
     * [season] optionally restricts the result to a single season.
     * When null, all episodes for all seasons are returned.
     */
    suspend fun fetchEpisodes(
        externalId: String,
        idType: ExternalIdType,
        season: Int? = null
    ): ProviderResult<List<Episode>>

    /**
     * Resolves an external ID from one scheme to another.
     *
     * For example, converting an IMDb ID to a TMDB ID when the caller
     * only has one form available.
     *
     * Returns null inside Success when a mapping exists but no target
     * ID could be found. Returns Failure only when the lookup itself errors.
     */
    suspend fun resolveExternalId(
        sourceId: String,
        sourceType: ExternalIdType,
        targetType: ExternalIdType
    ): ProviderResult<String?>

    /**
     * Searches for Media by free-text title.
     *
     * Added when TmdbMetadataProvider was built (real MetadataProvider
     * session). This lives here rather than on SearchProvider because it
     * is fundamentally a title-catalog lookup — the same kind of
     * operation as fetchMediaDetails(), just keyed by text instead of an
     * ID — not a stream/torrent discovery operation. SearchProvider's own
     * search() method exists for stream discovery by title, which most
     * real search backends (e.g. Torrentio, see
     * TorrentioSearchProvider's doc comment) cannot actually do; TMDB's
     * search/multi endpoint is a genuinely different capability that a
     * metadata catalog provider is positioned to offer.
     *
     * Returned Media objects come from this provider's search results
     * only (e.g. TMDB's /search/multi) — they are not yet enriched with
     * everything fetchMediaDetails() would provide (e.g. imdbId is
     * always null here; TMDB's search endpoints cannot return it — see
     * TmdbMetadataProvider's doc comment). Callers needing imdbId (e.g.
     * before calling SearchProvider.searchByMedia()) must call
     * fetchMediaDetails() separately once a specific Media is chosen.
     *
     * Returns an empty list (not a failure) for a query with zero
     * matches — a real "nothing found" is not an error condition.
     */
    suspend fun searchMedia(query: String): ProviderResult<List<Media>>
}

/**
 * Identifies which external ID scheme a string belongs to.
 */
enum class ExternalIdType {
    IMDB,
    TMDB,
    TVDB,
    TRAKT
}