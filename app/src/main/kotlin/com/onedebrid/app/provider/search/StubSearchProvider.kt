package com.onedebrid.app.provider.search

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.SearchResult
import javax.inject.Inject

class StubSearchProvider @Inject constructor() : SearchProvider {

    override val id: String = "stub_search"
    override val displayName: String = "Stub Search"

    override suspend fun search(
        query: String,
        filters: SearchFilters
    ): ProviderResult<SearchResult> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}