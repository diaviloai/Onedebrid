package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveStreamUseCase @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards DebridProvider>,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(candidate: StreamCandidate): RepositoryResult<StreamSource> =
        withContext(dispatchers.io) {
            if (providers.isEmpty()) {
                return@withContext RepositoryResult.Failure(AppError.AllProvidersUnavailable)
            }

            var lastError: AppError = AppError.AllProvidersUnavailable

            for (provider in providers) {
                when (val result = provider.resolveStream(candidate)) {
                    is ProviderResult.Success -> {
                        return@withContext RepositoryResult.Success(result.data)
                    }
                    is ProviderResult.Failure -> {
                        lastError = result.error.toAppError()
                    }
                }
            }

            // Return the actual error instead of masking it
            RepositoryResult.Failure(lastError)
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