package com.onedebrid.app.provider.metadata

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media

interface MetadataProvider {

    val id: String
    val displayName: String

    suspend fun fetchMediaDetails(
        externalId: String,
        idType: ExternalIdType
    ): ProviderResult<Media>

    suspend fun fetchEpisodes(
        externalId: String,
        idType: ExternalIdType,
        season: Int? = null
    ): ProviderResult<List<Episode>>

    suspend fun resolveExternalId(
        sourceId: String,
        sourceType: ExternalIdType,
        targetType: ExternalIdType
    ): ProviderResult<String?>

    // Added includeAdult parameter
    suspend fun searchMedia(query: String, includeAdult: Boolean = false): ProviderResult<List<Media>>
}

enum class ExternalIdType {
    IMDB,
    TMDB,
    TVDB,
    TRAKT
}