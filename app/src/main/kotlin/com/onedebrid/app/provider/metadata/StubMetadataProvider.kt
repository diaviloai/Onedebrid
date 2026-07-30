,package com.onedebrid.app.provider.metadata

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.provider.ProviderResult
import javax.inject.Inject

class StubMetadataProvider @Inject constructor() : MetadataProvider {

    override val id: String = "stub_metadata"

    override suspend fun fetchMediaDetails(
        mediaId: String
    ): ProviderResult<Media> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun fetchEpisodes(
        mediaId: String,
        season: Int
    ): ProviderResult<List<Episode>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun resolveExternalId(
        externalId: String,
        type: ExternalIdType
    ): ProviderResult<String?> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}