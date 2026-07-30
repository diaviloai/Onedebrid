package com.onedebrid.app.provider.debrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.ProviderResult
import javax.inject.Inject

class StubDebridProvider @Inject constructor() : DebridProvider {

    override val id: String = "stub_debrid"

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