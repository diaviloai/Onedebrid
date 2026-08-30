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

/**
 * SearchProvider implementation backed by the Torrentio addon
 * (https://torrentio.strem.fun), a free, keyless torrent-indexer
 * aggregator following the Stremio addon protocol.
 *
 * Session 29 — replaces StubSearchProvider as OneDebrid's first real
 * SearchProvider. See currentsprint.md Session 29 for the full design
 * discussion with Dia.
 *
 * IMPORTANT — search() vs searchByMedia():
 * Torrentio has no free-text search endpoint at all. Its only endpoint,
 * GET /stream/{type}/{id}.json, requires an already-known IMDb ID (and,
 * for series, a season/episode). It cannot fulfil SearchProvider.search()
 * (free-text title search) — that always returns
 * ProviderError.NotFound here, honestly reflecting that this provider
 * cannot do that job, rather than silently returning nothing or
 * pretending to search. Free-text search requires a different kind of
 * provider (e.g. TMDB-backed catalog search) that does not exist in this
 * codebase yet.
 *
 * searchByMedia() is where this provider actually does real work: given
 * a Media (and, for TV_SHOW, filters.season/filters.episode), it asks
 * Torrentio for stream candidates and maps them to StreamCandidate.
 *
 * Known API instability (flagged, not hidden): Torrentio has been
 * observed to sometimes omit `infoHash` from stream entries and instead
 * only provide a debrid `resolve` URL with the hash embedded in its path
 * (rivenmedia/riven#1342, Jan 2026). extractHash() below handles both
 * shapes. If Torrentio's response format changes further in a way this
 * provider doesn't anticipate, affected entries are dropped rather than
 * crashing or fabricating a hash — see mapToStreamCandidate().
 */
class TorrentioSearchProvider @Inject constructor(
    private val api: TorrentioApi
) : SearchProvider {

    override val id: String = "torrentio"
    override val displayName: String = "Torrentio"

    override suspend fun search(
        query: String,
        filters: SearchFilters
    ): ProviderResult<List<SearchResult>> =
        ProviderError.NotFound.asFailure()

    override suspend fun searchByMedia(
        media: Media,
        filters: SearchFilters
    ): ProviderResult<List<StreamCandidate>> {
        val imdbId = media.imdbId
            ?: return ProviderError.NotFound.asFailure()

        val type = when (media.type) {
            MediaType.MOVIE -> "movie"
            MediaType.TV_SHOW -> "series"
        }

        val lookupId = when (media.type) {
            MediaType.MOVIE -> imdbId
            MediaType.TV_SHOW -> {
                val season = filters.season
                val episode = filters.episode
                if (season == null || episode == null) {
                    // Torrentio's series endpoint requires both. Returning
                    // NotFound here is honest — without season/episode we
                    // cannot form a valid lookup at all, not a genuine
                    // "nothing found" from Torrentio itself.
                    return ProviderError.NotFound.asFailure()
                }
                "$imdbId:$season:$episode"
            }
        }

        return try {
            val response = api.getStreams(type = type, id = lookupId)
            val candidates = response.streams.mapNotNull { it.toStreamCandidate() }
            candidates.asSuccess()
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            // Covers no-connection and timeout cases (OkHttp/Okio throw
            // IOException for both — there is no separate exception type
            // to distinguish a timeout from a dropped connection here).
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    /**
     * Maps a single Torrentio stream entry to a StreamCandidate.
     *
     * Returns null (rather than throwing) for entries that cannot be
     * mapped meaningfully — e.g. no hash could be found by any means.
     * A candidate with no hash is useless to the rest of the app
     * (MediaRepositoryImpl.resolveStream() requires one), so dropping it
     * here is more honest than passing through a StreamCandidate that
     * can never actually be resolved.
     */
    private fun TorrentioStreamDto.toStreamCandidate(): StreamCandidate? {
        val hash = extractHash(this) ?: return null
        val rawTitle = title ?: name ?: "Unknown"

        return StreamCandidate(
            title = rawTitle,
            hash = hash,
            magnetUrl = null, // Torrentio does not expose raw magnet links
            sizeBytes = behaviorHints?.videoSize ?: parseSizeFromTitle(rawTitle),
            seeders = parseSeedersFromTitle(rawTitle),
            quality = parseQualityFromTitle(rawTitle)
        )
    }

    /**
     * Extracts a 40-character hex info-hash from a Torrentio stream entry.
     *
     * Prefers the explicit [TorrentioStreamDto.infoHash] field. Falls back
     * to scanning [TorrentioStreamDto.url] for a 40-hex-character path
     * segment — Torrentio's debrid resolve URLs are shaped like
     * `.../resolve/realdebrid/<key>/<hash>/...`, so the hash appears as
     * its own path segment. This fallback exists because of the
     * infoHash-omission behavior flagged in this file's class doc comment.
     */
    private fun extractHash(dto: TorrentioStreamDto): String? {
        dto.infoHash?.takeIf { it.isValidHash() }?.let { return it }

        val url = dto.url ?: return null
        return url.split("/").firstOrNull { it.isValidHash() }
    }

    private fun String.isValidHash(): Boolean =
        length == 40 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    /**
     * Torrentio packs quality, size, and seeders into the free-text
     * `title` field rather than structured fields (aside from
     * behaviorHints.videoSize for size). These parsers are deliberately
     * simple pattern matches against common release-naming conventions —
     * they will not catch every possible format, and that is an accepted
     * tradeoff: a StreamCandidate with VideoQuality.UNKNOWN or a null
     * seeder count is still usable, per VideoQuality's own doc comment
     * ("UNKNOWN... prevents null handling at call sites while still
     * representing uncertainty").
     */
    private fun parseQualityFromTitle(title: String): VideoQuality {
        val lower = title.lowercase()
        return when {
            "2160p" in lower || "4k" in lower || "uhd" in lower -> VideoQuality.UHD_4K
            "1080p" in lower -> VideoQuality.HD_1080
            "720p" in lower -> VideoQuality.HD_720
            "480p" in lower || "sd" in lower -> VideoQuality.SD
            else -> VideoQuality.UNKNOWN
        }
    }

    /**
     * Torrentio titles commonly include a size line like "💾 1.4 GB" or
     * "1.4GB". Matches the common GB/MB pattern; returns null if not found
     * (behaviorHints.videoSize, checked first by the caller, is the more
     * reliable source when present).
     */
    private fun parseSizeFromTitle(title: String): Long? {
        val match = Regex("""(\d+(?:\.\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
            .find(title) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        val bytes = when (unit) {
            "GB" -> value * 1_000_000_000
            "MB" -> value * 1_000_000
            else -> return null
        }
        return bytes.toLong()
    }

    /**
     * Torrentio titles commonly include a seeder count like "👤 42".
     * Returns null if not found — seeders are informational only and
     * not required for a candidate to be usable.
     */
    private fun parseSeedersFromTitle(title: String): Int? =
        Regex("""👤\s*(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

    private fun HttpException.toProviderError(): ProviderError = when (code()) {
        401, 403 -> ProviderError.AuthenticationFailed
        404 -> ProviderError.NotFound
        429 -> ProviderError.RateLimited(retryAfterSeconds = null)
        in 500..599 -> ProviderError.ServiceUnavailable
        else -> ProviderError.ParsingError(cause = this)
    }
}