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
import com.onedebrid.app.provider.search.SearchFilters
import com.onedebrid.app.provider.search.SearchProvider

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val metadataProvider: MetadataProvider,
    private val debridProvider: DebridProvider,
    private val searchProvider: SearchProvider,
    private val mediaCache: MediaCache,
    private val dispatchers: CoroutineDispatchers
) : MediaRepository {

    override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> =
        withContext(dispatchers.io) {
            mediaCache.getMedia(mediaId)?.let { cached ->
                return@withContext RepositoryResult.Success(cached)
            }
            metadataProvider.fetchMediaDetails(
                externalId = mediaId,
                idType = ExternalIdType.TMDB
            ).toRepositoryResult().also { result ->
                if (result is RepositoryResult.Success) {
                    mediaCache.putMedia(result.data)
                }
            }
        }

    override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> =
        withContext(dispatchers.io) {
            mediaCache.getEpisodes(mediaId)?.let { cached ->
                return@withContext RepositoryResult.Success(cached)
            }
            metadataProvider.fetchEpisodes(
                externalId = mediaId,
                idType = ExternalIdType.TMDB
            ).toRepositoryResult().also { result ->
                if (result is RepositoryResult.Success) {
                    mediaCache.putEpisodes(mediaId, result.data)
                }
            }
        }

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
        withContext(dispatchers.io) {
            debridProvider.resolveStream(candidate).toRepositoryResult()
        }

    override suspend fun checkCacheStatus(
        candidates: List<StreamCandidate>
    ): RepositoryResult<Map<String, Boolean>> =
        withContext(dispatchers.io) {
            if (candidates.isEmpty()) {
                return@withContext RepositoryResult.Success(emptyMap())
            }
            debridProvider.checkCacheStatus(candidates).toRepositoryResult()
        }

    override suspend fun search(
        query: String,
        profileId: String
    ): RepositoryResult<List<SearchResult>> =
        withContext(dispatchers.io) {
            metadataProvider.searchMedia(query)
                .toRepositoryResult()
                .let { result ->
                    when (result) {
                        is RepositoryResult.Success -> RepositoryResult.Success(
                            result.data.map { media ->
                                SearchResult(
                                    media = media,
                                    candidates = emptyList(),
                                    sourceProvider = metadataProvider.id
                                )
                            }
                        )
                        is RepositoryResult.Failure -> result
                    }
                }
        }

    override suspend fun searchStreamsByMedia(
        media: Media,
        episode: Episode?
    ): RepositoryResult<List<StreamCandidate>> =
        withContext(dispatchers.io) {
            val filters = SearchFilters(
                season = episode?.seasonNumber,
                episode = episode?.episodeNumber
            )
            searchProvider.searchByMedia(media, filters).toRepositoryResult()
        }

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