package com.onedebrid.app.provider.subtitle

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.model.SubtitleTrack
import com.onedebrid.app.provider.ProviderResult
import javax.inject.Inject

class StubSubtitleProvider @Inject constructor() : SubtitleProvider {

    override val id: String = "stub_subtitle"

    override suspend fun searchSubtitles(
        query: SubtitleQuery
    ): ProviderResult<List<SubtitleTrack>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun downloadSubtitle(
        subtitleId: String
    ProviderResult<SubtitleTrack> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}