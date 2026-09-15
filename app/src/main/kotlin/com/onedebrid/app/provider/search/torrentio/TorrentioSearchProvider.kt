package com.onedebrid.app.provider.search.torrentio

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.provider.search.SearchProvider
import kotlinx.serialization.SerializationException
import okio.IOException
import retrofit2.HttpException
import javax.inject.Inject

/**
 * SearchProvider backed by the Torrentio Stremio Addon endpoint
 * (https://torrentio.strem.fun/stream/{type}/{id}.json).
 *
 * Scrapes torrent source candidates using IMDb identifiers (e.g., tt0137523).
 */
class TorrentioSearchProvider @Inject constructor(
    private val api: TorrentioApi
) : SearchProvider {

    override val id: String = "torrentio"
    override val displayName: String = "Torrentio"

    override suspend fun searchByMedia(
        media: Media,
        episode: Episode?
    ): ProviderResult<List<SearchResult>> {
        val imdbId = media.imdbId
            ?: return ProviderError.NotFound.asFailure()

        val typePath = when (media.type) {
            MediaType.MOVIE -> "movie"
            MediaType.TV_SHOW -> "series"
        }

        val requestIdentifier = if (media.type == MediaType.TV_SHOW && episode != null) {
            "$imdbId:${episode.seasonNumber}:${episode.episodeNumber}"
        } else {
            imdbId
        }

        return try {
            val response = api.getStreams(type = typePath, id = requestIdentifier)
            val results = response.streams.mapNotNull { it.toSearchResult() }
            results.asSuccess()
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    private fun TorrentioStreamDto.toSearchResult(): SearchResult? {
        val hash = infoHash ?: extractInfoHashFromMagnet(magnetUrl) ?: return null

        return SearchResult(
            id = hash,
            title = title ?: name ?: "Unknown Release",
            infoHash = hash,
            fileIndex = fileIdx,
            seeds = seeders ?: 0,
            sizeBytes = size ?: 0L,
            sourceProvider = id
        )
    }

    private fun extractInfoHashFromMagnet(magnet: String?): String? {
        if (magnet == null) return null
        val regex = Regex("btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})", RegexOption.IGNORE_CASE)
        return regex.find(magnet)?.groupValues?.get(1)
    }

    private fun HttpException.toProviderError(): ProviderError = when (code()) {
        401, 403 -> ProviderError.AuthenticationFailed
        404 -> ProviderError.NotFound
        429 -> ProviderError.RateLimited(retryAfterSeconds = null)
        in 500..599 -> ProviderError.ServiceUnavailable
        else -> ProviderError.ParsingError(cause = this)
    }
}
