package com.onedebrid.app.provider.subtitle

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.SubtitleTrack
import javax.inject.Inject

class StubSubtitleProvider @Inject constructor() : SubtitleProvider {

    override val id: String = "stub_subtitle"
    override val displayName: String = "Stub Subtitle"

    override suspend fun searchSubtitles(
        query: SubtitleQuery
    ): ProviderResult<List<SubtitleTrack>> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)

    override suspend fun downloadSubtitle(
        downloadUrl: String
    ): ProviderResult<SubtitleTrack> =
        ProviderResult.Failure(ProviderError.ServiceUnavailable)
}