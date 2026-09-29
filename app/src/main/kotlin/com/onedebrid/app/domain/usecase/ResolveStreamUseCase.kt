package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a playable [StreamSource] for a given media hash.
 *
 * Iterates through configured [DebridProvider]s. If a provider fails to
 * resolve a stream or returns an uncached link, the use case gracefully
 * falls back to the next provider until a stream is successfully resolved
 * or all providers are exhausted.
 */
@Singleton
class ResolveStreamUseCase @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards DebridProvider>,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(hash: String): RepositoryResult<StreamSource> =
        withContext(dispatchers.io) {
            if (providers.isEmpty()) {
                return@withContext RepositoryResult.Failure(AppError.AllProvidersUnavailable)
            }

            var lastError: AppError = AppError.AllProvidersUnavailable

            for (provider in providers) {
                when (val result = provider.resolveStream(hash)) {
                    is ProviderResult.Success -> {
                        return@withContext RepositoryResult.Success(result.data)
                    }
                    is ProviderResult.Failure -> {
                        lastError = result.error.toAppError()
                    }
                }
            }

            RepositoryResult.Failure(
                if (lastError is AppError.AllProvidersUnavailable) {
                    AppError.AllProvidersUnavailable
                } else {
                    AppError.StreamResolutionFailed(
                        cause = Exception("All providers failed to resolve stream. Last error: $lastError")
                    )
                }
            )
        }

    private fun ProviderError.toAppError(): AppError = when (this) {
        is ProviderError.AuthenticationFailed -> AppError.NotAuthenticated
        is ProviderError.NetworkError -> AppError.NoNetworkConnection
        is ProviderError.ServiceUnavailable -> AppError.AllProvidersUnavailable
        is ProviderError.RateLimited -> AppError.AllProvidersUnavailable
        is ProviderError.NotFound -> AppError.NoCachedStreamAvailable
        is ProviderError.ParsingError -> AppError.Unknown(
            message = "Parsing error: ${cause.message}"
        )
    }
}
