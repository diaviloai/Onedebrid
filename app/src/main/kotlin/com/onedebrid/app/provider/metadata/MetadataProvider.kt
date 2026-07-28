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