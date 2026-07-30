package com.onedebrid.app.provider.debrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamSource
import javax.inject.Inject

class StubDebridProvider @Inject constructor() : DebridProvider {

    override val id: String = "stub_debrid"
    override val displayName: String = "Stub Debrid"

    override suspend fun verifyAccount(): ProviderResult<AccountInfo> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun checkCache(
        hashes: List<String>
    ): ProviderResult<Map<String, Boolean>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun resolveStream(
        hash: String
    ): ProviderResult<StreamSource> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}