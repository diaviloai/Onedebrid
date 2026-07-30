package com.onedebrid.app.provider.metadata

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.error.ProviderResult
import javax.inject.Inject

class StubMetadataProvider @Inject constructor() : MetadataProvider {

    override val id: String = "stub_metadata"
    override val displayName: String = "Stub Metadata"

    override suspend fun fetchMediaDetails(
        externalId: String,
        idType: ExternalIdType
    ): ProviderResult<Media> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun fetchEpisodes(
        externalId: String,
        idType: ExternalIdType,
        season: Int?
    ): ProviderResult<List<Episode>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun resolveExternalId(
        sourceId: String,
        sourceType: ExternalIdType,
        targetType: ExternalIdType
    ): ProviderResult<String?> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}