package com.onedebrid.app.data.repository

import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.SubtitleTrack
import com.onedebrid.app.provider.subtitle.SubtitleProvider
import com.onedebrid.app.provider.subtitle.SubtitleQuery
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backed by StubSubtitleProvider until a real implementation exists.
 * All calls will return RepositoryResult.Failure(AppError.AllProvidersUnavailable)
 * until the stub is replaced. This is intentional.
 */
@Singleton
class SubtitleRepositoryImpl @Inject constructor(
    private val subtitleProvider: SubtitleProvider,
    private val dispatchers: CoroutineDispatchers
) : SubtitleRepository {

    override suspend fun searchSubtitles(
        query: SubtitleQuery
    ): RepositoryResult<List<SubtitleTrack>> =
        withContext(dispatchers.io) {
            subtitleProvider.searchSubtitles(query).toRepositoryResult()
        }

    override suspend fun downloadSubtitle(
        track: SubtitleTrack
    ): RepositoryResult<SubtitleTrack> =
        withContext(dispatchers.io) {
            subtitleProvider.downloadSubtitle(track.url).toRepositoryResult()
        }

    // --- ProviderResult → RepositoryResult translation ---

    private fun <T> ProviderResult<T>.toRepositoryResult(): RepositoryResult<T> =
        when (this) {
            is ProviderResult.Success -> RepositoryResult.Success(data)
            is ProviderResult.Failure -> RepositoryResult.Failure(error.toAppError())
        }

    private fun ProviderError.toAppError(): AppError = when (this) {
        is ProviderError.AuthenticationFailed -> AppError.NotAuthenticated
        is ProviderError.NetworkError -> AppError.NoNetworkConnection
        is ProviderError.ServiceUnavailable -> AppError.AllProvidersUnavailable
        is ProviderError.RateLimited -> AppError.AllProvidersUnavailable
        is ProviderError.NotFound -> AppError.Unknown(message = "Subtitle not found")
        is ProviderError.ParsingError -> AppError.Unknown(
            message = "Parsing error: ${cause.message}"
        )
    }
}