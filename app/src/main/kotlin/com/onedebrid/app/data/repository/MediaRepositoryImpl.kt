package com.onedebrid.app.data.repository

import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import com.onedebrid.app.provider.metadata.ExternalIdType
import com.onedebrid.app.provider.metadata.MetadataProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backed by stub providers until real provider implementations exist.
 * All calls will return RepositoryResult.Failure(AppError.AllProvidersUnavailable)
 * until StubDebridProvider and StubMetadataProvider are replaced.
 * This is intentional — accidental runtime calls fail visibly.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val metadataProvider: MetadataProvider,
    private val debridProvider: DebridProvider,
    private val dispatchers: CoroutineDispatchers
) : MediaRepository {

    override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> =
        withContext(dispatchers.io) {
            metadataProvider.fetchMediaDetails(
                externalId = mediaId,
                idType = ExternalIdType.IMDB
            ).toRepositoryResult()
        }

    override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> =
        withContext(dispatchers.io) {
            metadataProvider.fetchEpisodes(
                externalId = mediaId,
                idType = ExternalIdType.IMDB
            ).toRepositoryResult()
        }

    override suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource> =
        withContext(dispatchers.io) {
            val hash = candidate.hash
                ?: return@withContext RepositoryResult.Failure(
                    AppError.NoCachedStreamAvailable
                )
            debridProvider.resolveStream(hash).toRepositoryResult()
        }

    override suspend fun checkCacheStatus(
        candidates: List<StreamCandidate>
    ): RepositoryResult<Map<String, Boolean>> =
        withContext(dispatchers.io) {
            // Candidates without a hash cannot be cache-checked.
            // They are excluded from the batch and implicitly treated as
            // not cached — the map will simply not contain an entry for them.
            val hashes = candidates.mapNotNull { it.hash }
            if (hashes.isEmpty()) {
                return@withContext RepositoryResult.Success(emptyMap())
            }
            debridProvider.checkCache(hashes).toRepositoryResult()
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
        is ProviderError.NotFound -> AppError.NoCachedStreamAvailable
        is ProviderError.ParsingError -> AppError.Unknown(
            message = "Parsing error: ${cause.message}"
        )
    }
}