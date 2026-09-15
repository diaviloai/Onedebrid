package com.onedebrid.app.provider.search.torrentio

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.provider.search.SearchFilters
import com.onedebrid.app.provider.search.SearchProvider
import kotlinx.serialization.SerializationException
import okio.IOException
import retrofit2.HttpException
import javax.inject.Inject

class TorrentioSearchProvider @Inject constructor(
    private val api: TorrentioApi
) : SearchProvider {

    override val id: String = "torrentio"
    override val displayName: String = "Torrentio"

    override suspend fun search(
        query: String,
        filters: SearchFilters
    ): ProviderResult<List<SearchResult>> {
        // Torrentio cannot perform free-text search.
        return ProviderError.NotFound.asFailure()
    }

    override suspend fun searchByMedia(
        media: Media,
        filters: SearchFilters
    ): ProviderResult<List<StreamCandidate>> {
        val imdbId = media.imdbId
            ?: return ProviderError.NotFound.asFailure()

        val typePath = when (media.type) {
            MediaType.MOVIE -> "movie"
            MediaType.TV_SHOW -> "series"
        }

        val requestIdentifier = if (media.type == MediaType.TV_SHOW) {
            val season = filters.season
            val episode = filters.episode
            if (season != null && episode != null) {
                "$imdbId:$season:$episode"
            } else {
                return ProviderError.NotFound.asFailure()
            }
        } else {
            imdbId
        }

        return try {
            val response = api.getStreams(type = typePath, id = requestIdentifier)
            val candidates = response.streams.mapNotNull { it.toStreamCandidate() }
            candidates.asSuccess()
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    private fun TorrentioStreamDto.toStreamCandidate(): StreamCandidate? {
        val hash = infoHash ?: extractHashFromUrl(url) ?: return null
        val fullTitle = title ?: name ?: "Unknown Release"

        return StreamCandidate(
            title = fullTitle,
            hash = hash,
            magnetUrl = url,
            sizeBytes = behaviorHints?.videoSize ?: parseSizeBytes(fullTitle),
            seeders = parseSeeders(fullTitle),
            quality = parseQuality(fullTitle)
        )
    }

    private fun extractHashFromUrl(url: String?): String? {
        if (url == null) return null
        // Fallback for Torrentio URL debrid resolve format containing the hash
        val match = Regex("/([a-fA-F0-9]{40})(/|$)").find(url)
        return match?.groupValues?.get(1)
    }

    private fun parseQuality(title: String): VideoQuality = when {
        title.contains("4k", ignoreCase = true) || title.contains("2160p", ignoreCase = true) -> VideoQuality.UHD_4K
        title.contains("1080p", ignoreCase = true) -> VideoQuality.HD_1080
        title.contains("720p", ignoreCase = true) -> VideoQuality.HD_720
        title.contains("480p", ignoreCase = true) || title.contains("SD", ignoreCase = true) -> VideoQuality.SD
        else -> VideoQuality.UNKNOWN
    }

    private fun parseSeeders(title: String): Int {
        val match = Regex("👤\\s*(\\d+)").find(title)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun parseSizeBytes(title: String): Long? {
        val match = Regex("💾\\s*([\\d.]+)\\s*(GB|MB)", RegexOption.IGNORE_CASE).find(title)
            ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        return when (unit) {
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            else -> null
        }
    }

    private fun HttpException.toProviderError(): ProviderError = when (code()) {
        401, 403 -> ProviderError.AuthenticationFailed
        404 -> ProviderError.NotFound
        429 -> ProviderError.RateLimited(retryAfterSeconds = null)
        in 500..599 -> ProviderError.ServiceUnavailable
        else -> ProviderError.ParsingError(cause = this)
    }
}
