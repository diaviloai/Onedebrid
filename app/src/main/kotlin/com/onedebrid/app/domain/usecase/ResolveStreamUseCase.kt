package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridStreamProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a playable [StreamSource] for a given [PlaybackRequest].
 *
 * Iterates through configured [DebridStreamProvider]s in priority order.
 * If a provider fails to resolve a stream or returns an un-cached link,
 * the use case gracefully falls back to the next provider until a stream
 * is successfully resolved or all providers are exhausted.
 */
@Singleton
class ResolveStreamUseCase @Inject constructor(
    private val providers: List<@JvmSuppressWildcards DebridStreamProvider>,
    private val dispatchers: CoroutineDispatchers
) {

    suspend operator fun invoke(request: PlaybackRequest): RepositoryResult<StreamSource> =
        withContext(dispatchers.io) {
            val enabledProviders = providers.filter { it.isEnabled() }
            if (enabledProviders.isEmpty()) {
                return@withContext RepositoryResult.Failure(AppError.AllProvidersUnavailable)
            }

            var lastError: AppError = AppError.AllProvidersUnavailable

            for (provider in enabledProviders) {
                when (val result = provider.resolveStream(request)) {
                    is RepositoryResult.Success -> {
                        return@withContext result
                    }
                    is RepositoryResult.Failure -> {
                        lastError = result.error
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
}
