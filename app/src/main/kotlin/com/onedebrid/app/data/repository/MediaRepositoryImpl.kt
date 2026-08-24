package com.onedebrid.app.data.repository

import com.onedebrid.app.data.local.MediaCache
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
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.provider.search.SearchProvider

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
    private val searchProvider: SearchProvider,
    private val mediaCache: MediaCache,
    private val dispatchers: CoroutineDispatchers
) : MediaRepository {

    /**
     * Cache-first (Session 25). Checks MediaCache before hitting the
     * network; writes through to MediaCache on a successful fetch. A cache
     * hit skips metadataProvider entirely, so this also works while
     * MetadataProvider is still StubMetadataProvider — as long as a Media
     * for this mediaId was cached previously (e.g. by a future real
     * provider, or a test seeding the cache directly), getMediaDetails()
     * will succeed without needing a real network call. On a cache miss
     * today, this still resolves to AppError.AllProvidersUnavailable via
     * the stub, exactly as before this change — the cache does not alter
     * failure behavior, only skips redundant work on a hit.
     */
    override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> =
        withContext(dispatchers.io) {
            mediaCache.getMedia(mediaId)?.let { cached ->
                return@withContext RepositoryResult.Success(cached)
            }
            metadataProvider.fetchMediaDetails(
                externalId = mediaId,
                idType = ExternalIdType.IMDB
            ).toRepositoryResult().also { result ->
                if (result is RepositoryResult.Success) {
                    mediaCache.putMedia(result.data)
                }
            }
        }

    /**
     * Cache-first, same pattern as getMediaDetails() above.
     */
    override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> =
        withContext(dispatchers.io) {
            mediaCache.getEpisodes(mediaId)?.let { cached ->
                return@withContext RepositoryResult.Success(cached)
            }
            metadataProvider.fetchEpisodes(
                externalId = mediaId,
                idType = ExternalIdType.IMDB
            ).toRepositoryResult().also { result ->
                if (result is RepositoryResult.Success) {
                    mediaCache.putEpisodes(mediaId, result.data)
                }
            }
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

    override suspend fun search(
        query: String,
        profileId: String
    ): RepositoryResult<List<SearchResult>> =
        withContext(dispatchers.io) {
            searchProvider.search(query).toRepositoryResult()
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
}/**
     * Implemented via getEpisodes() (cache-first) plus an in-memory filter
     * to the matching episodeId. See the doc comment on this method in
     * MediaRepository.kt for why there is no dedicated per-episode cache
     * entry or provider call as of Session 27.
     */
    override suspend fun getEpisodeById(
        mediaId: String,
        episodeId: String
    ): RepositoryResult<Episode> =
        when (val episodesResult = getEpisodes(mediaId)) {
            is RepositoryResult.Success -> {
                val match = episodesResult.data.find { it.id == episodeId }
                if (match != null) {
                    RepositoryResult.Success(match)
                } else {
                    RepositoryResult.Failure(
                        AppError.Unknown(
                            message = "Episode not found: $episodeId (mediaId: $mediaId)"
                        )
                    )
                }
            }
            is RepositoryResult.Failure -> episodesResult
        }

    override suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource> =