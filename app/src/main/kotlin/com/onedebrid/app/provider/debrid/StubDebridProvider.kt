package com.onedebrid.app.provider.debrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import javax.inject.Inject

class StubDebridProvider @Inject constructor() : DebridProvider {

    override val id: String = "stub_debrid"
    override val displayName: String = "Stub Debrid"

    override suspend fun checkCacheStatus(
        candidates: List<StreamCandidate>
    ): ProviderResult<Map<String, Boolean>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun resolveStream(
        candidate: StreamCandidate
    ): ProviderResult<StreamSource> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}