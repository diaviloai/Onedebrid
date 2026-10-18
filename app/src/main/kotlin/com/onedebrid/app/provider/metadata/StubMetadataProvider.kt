package com.onedebrid.app.provider.metadata

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import javax.inject.Inject

class StubMetadataProvider @Inject constructor() : MetadataProvider {

    override val id: String = "stub_metadata"
    override val displayName: String = "Stub Metadata"

    override suspend fun fetchMediaDetails(
        externalId: String,
        idType: ExternalIdType
    ): ProviderResult<Media> =
        ProviderError.ServiceUnavailable.asFailure()

    override suspend fun fetchEpisodes(
        externalId: String,
        idType: ExternalIdType,
        season: Int?
    ): ProviderResult<List<Episode>> =
        ProviderError.ServiceUnavailable.asFailure()

    override suspend fun resolveExternalId(
        sourceId: String,
        sourceType: ExternalIdType,
        targetType: ExternalIdType
    ): ProviderResult<String?> =
        ProviderError.ServiceUnavailable.asFailure()

    // Updated to include the includeAdult parameter
    override suspend fun searchMedia(
        query: String, 
        includeAdult: Boolean
    ): ProviderResult<List<Media>> =
        ProviderError.ServiceUnavailable.asFailure()
}