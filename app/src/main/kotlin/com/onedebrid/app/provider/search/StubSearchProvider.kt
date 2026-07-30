package com.onedebrid.app.provider.search

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.provider.ProviderResult
import javax.inject.Inject

class StubSearchProvider @Inject constructor() : SearchProvider {

    override val id: String = "stub_search"

    override suspend fun search(
        query: String,
        filters: SearchFilters
    ): ProviderResult<List<SearchResult>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}