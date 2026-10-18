package com.onedebrid.app.provider.debrid.realdebrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class RealDebridProvider @Inject constructor(
    private val api: RealDebridApi
) : DebridProvider {

    override val id: String = "realdebrid"
    override val displayName: String = "Real-Debrid"

    /**
     * Cache detection by probing the user's own Real-Debrid account.
     *
     * Real-Debrid disabled torrents/instantAvailability in Nov 2024
     * (error_code 37, "disabled_endpoint"), so there is no direct lookup.
     * Instead, for a limited number of candidates we: add the magnet, select
     * all files, inspect the torrent, and ALWAYS delete it again. A torrent
     * whose status is "downloaded" right after file selection was already
     * cached on Real-Debrid.
     *
     * Contract: the returned map only contains hashes that were actually
     * checked. A hash that is absent is UNKNOWN (not probed, or the probe
     * was inconclusive), never "uncached". Callers must not treat absence
     * as false.
     *
     * Candidates are tried best-first until [MAX_PROBES] conclusive results
     * are collected (a few more may land because probes run in parallel) or
     * [MAX_ATTEMPTS] candidates have been tried. Errors are classified:
     * - Per-torrent errors (e.g. error 35 "infringing file", blocked or
     *   invalid hashes) skip that candidate and move on to the next.
     * - Global errors (rate limit, too many active downloads, fair usage)
     *   stop probing but keep results already gathered.
     * - Auth errors stop probing and, if nothing was determined, return
     *   AuthenticationFailed.
     */
    override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): ProviderResult<Map<String, Boolean>> {
        if (candidates.isEmpty()) return emptyMap<String, Boolean>().asSuccess()

        val queue = ConcurrentLinkedQueue(selectProbeTargets(candidates))
        val results = ConcurrentHashMap<String, Boolean>()
        val concluded = AtomicInteger(0)
        val stopProbing = AtomicBoolean(false)
        val authFailed = AtomicBoolean(false)

        coroutineScope {
            List(PROBE_CONCURRENCY) {
                async {
                    while (!stopProbing.get() && concluded.get() < MAX_PROBES) {
                        val candidate = queue.poll() ?: break
                        try {
                            val cached = probeCacheStatus(candidate)
                            if (cached != null) {
                                results[candidate.hash] = cached
                                concluded.incrementAndGet()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HttpException) {
                            when (classifyHttpFailure(e)) {
                                HttpFailure.SKIP_CANDIDATE -> Unit
                                HttpFailure.STOP_PROBING -> stopProbing.set(true)
                                HttpFailure.AUTH -> {
                                    authFailed.set(true)
                                    stopProbing.set(true)
                                }
                            }
                        } catch (e: Exception) {
                            // Network hiccup or malformed response: leave this hash unknown.
                        }
                    }
                }
            }.awaitAll()
        }

        return if (results.isEmpty() && authFailed.get()) {
            ProviderError.AuthenticationFailed.asFailure()
        } else {
            results.toMap().asSuccess()
        }
    }

    override suspend fun resolveStream(candidate: StreamCandidate): ProviderResult<StreamSource> {
        return try {
            val magnetOrLink = candidate.magnetUrl

            if (magnetOrLink != null && magnetOrLink.startsWith("http")) {
                if (magnetOrLink.contains("real-debrid.com")) {
                    val unrestrictResponse = api.unrestrictLink(magnetOrLink)
                    val finalLink = unrestrictResponse.link ?: return ProviderError.NotFound.asFailure()
                    
                    return StreamSource(
                        id = unrestrictResponse.id ?: candidate.hash,
                        mediaId = "", 
                        url = finalLink,
                        quality = candidate.quality,
                        fileSizeBytes = unrestrictResponse.filesize ?: candidate.sizeBytes ?: 0L,
                        fileName = unrestrictResponse.filename ?: "video.mp4",
                        isCached = true
                    ).asSuccess()
                } else {
                    return StreamSource(
                        id = candidate.hash,
                        mediaId = "", 
                        url = magnetOrLink,
                        quality = candidate.quality,
                        fileSizeBytes = candidate.sizeBytes ?: 0L,
                        fileName = "video.mp4",
                        isCached = true
                    ).asSuccess()
                }
            }

            val magnet = if (magnetOrLink != null && magnetOrLink.startsWith("magnet:")) {
                magnetOrLink
            } else {
                "magnet:?xt=urn:btih:${candidate.hash}"
            }
            
            val addResponse = api.addMagnet(magnet)
            val torrentId = addResponse.id ?: return ProviderError.NotFound.asFailure()

            var infoResponse = api.getTorrentInfo(torrentId)
            var attempts = 0
            while (infoResponse.status == "magnet_conversion" && attempts < 10) {
                delay(1000)
                infoResponse = api.getTorrentInfo(torrentId)
                attempts++
            }
            
            if (infoResponse.status == "waiting_files_selection") {
                val allFiles = infoResponse.files ?: emptyList()
                val videoFiles = allFiles.filter { 
                    val path = it.path?.lowercase() ?: ""
                    path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") 
                }
                
                // Fallback to largest file overall if no standard video extension is found
                val selectedFile = videoFiles.maxByOrNull { it.bytes ?: 0L } 
                    ?: allFiles.maxByOrNull { it.bytes ?: 0L }
                    ?: return ProviderError.NotFound.asFailure()

                val fileId = selectedFile.id ?: return ProviderError.NotFound.asFailure()
                api.selectFiles(torrentId, fileId.toString())
                
                attempts = 0
                infoResponse = api.getTorrentInfo(torrentId)
                while (infoResponse.links.isNullOrEmpty() && attempts < 10) {
                    delay(1000)
                    infoResponse = api.getTorrentInfo(torrentId)
                    attempts++
                }
            }

            val links = infoResponse.links ?: emptyList()
            val link = links.firstOrNull() 
                ?: return ProviderError.NotFound.asFailure()

            val unrestrictResponse = api.unrestrictLink(link)
            val finalLink = unrestrictResponse.link ?: return ProviderError.NotFound.asFailure()

            StreamSource(
                id = unrestrictResponse.id ?: torrentId,
                mediaId = "", 
                url = finalLink,
                quality = candidate.quality,
                fileSizeBytes = unrestrictResponse.filesize ?: 0L,
                fileName = unrestrictResponse.filename ?: "video.mp4",
                isCached = true
            ).asSuccess()

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            
            if (e is HttpException) {
                when (e.code()) {
                    401, 403 -> return ProviderError.AuthenticationFailed.asFailure()
                    429 -> return ProviderError.RateLimited(null).asFailure()
                }
            }
            
            ProviderError.NotFound.asFailure()
        }
    }

    // ---------------------------------------------------------------------
    // Cache probing helpers
    // ---------------------------------------------------------------------

    /**
     * Orders candidates for probing: best quality first, then most seeders,
     * de-duplicated by hash, capped at [MAX_ATTEMPTS] so we never hammer the
     * account or exhaust active-download slots.
     */
    private fun selectProbeTargets(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .distinctBy { it.hash.lowercase() }
            .sortedWith(
                compareByDescending<StreamCandidate> { qualityRank(it.quality) }
                    .thenByDescending { it.seeders }
            )
            .take(MAX_ATTEMPTS)

    private fun qualityRank(quality: VideoQuality): Int = when (quality) {
        VideoQuality.UHD_4K -> 4
        VideoQuality.HD_1080 -> 3
        VideoQuality.HD_720 -> 2
        VideoQuality.SD -> 1
        VideoQuality.UNKNOWN -> 0
    }

    private fun buildMagnet(candidate: StreamCandidate): String {
        val existing = candidate.magnetUrl
        return if (existing != null && existing.startsWith("magnet:")) {
            existing
        } else {
            "magnet:?xt=urn:btih:${candidate.hash}"
        }
    }

    /**
     * Probes a single candidate. Returns true (cached), false (not cached),
     * or null (inconclusive). The probe torrent is always deleted, even if
     * the coroutine is cancelled mid-probe.
     */
    private suspend fun probeCacheStatus(candidate: StreamCandidate): Boolean? {
        val torrentId = api.addMagnet(buildMagnet(candidate)).id ?: return null

        try {
            var info = api.getTorrentInfo(torrentId)

            var polls = 0
            while (info.status == "magnet_conversion" && polls < MAGNET_CONVERSION_POLLS) {
                delay(MAGNET_CONVERSION_DELAY_MS)
                info = api.getTorrentInfo(torrentId)
                polls++
            }

            if (info.status == "waiting_files_selection") {
                api.selectFiles(torrentId, "all")
                info = api.getTorrentInfo(torrentId)
            }

            // A cached torrent flips to "downloaded" almost immediately after
            // file selection. Give it a few short polls before concluding.
            polls = 0
            while (info.status != "downloaded" &&
                info.status in SETTLING_STATUSES &&
                polls < SETTLE_POLLS
            ) {
                delay(SETTLE_DELAY_MS)
                info = api.getTorrentInfo(torrentId)
                polls++
            }

            return when (info.status) {
                "downloaded" -> true
                "queued", "downloading", "magnet_error", "error", "virus", "dead" -> false
                else -> null
            }
        } finally {
            deleteProbeTorrent(torrentId)
        }
    }

    private enum class HttpFailure { SKIP_CANDIDATE, STOP_PROBING, AUTH }

    /**
     * Decides what an HTTP error during a probe means. Real-Debrid puts a
     * numeric "error_code" in the JSON error body; that is more reliable
     * than the HTTP status, so it is checked first.
     * See https://api.real-debrid.com/ for the code list.
     */
    private fun classifyHttpFailure(e: HttpException): HttpFailure {
        val errorCode = realDebridErrorCode(e)
        return when {
            errorCode == ERR_INFRINGING_FILE -> HttpFailure.SKIP_CANDIDATE
            errorCode in AUTH_ERROR_CODES || e.code() == 401 -> HttpFailure.AUTH
            errorCode == null && e.code() == 403 -> HttpFailure.AUTH
            errorCode in STOP_ERROR_CODES || e.code() == 429 -> HttpFailure.STOP_PROBING
            else -> HttpFailure.SKIP_CANDIDATE
        }
    }

    private fun realDebridErrorCode(e: HttpException): Int? =
        try {
            val body = e.response()?.errorBody()?.string()
            if (body == null) null else ERROR_CODE_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull()
        } catch (ex: Exception) {
            null
        }

    /**
     * Removes a probe torrent from the account. Runs in NonCancellable so a
     * cancelled probe still cleans up, and retries once. Failures are
     * swallowed: a leftover probe torrent is a nuisance, not a reason to fail.
     */
    private suspend fun deleteProbeTorrent(torrentId: String) {
        withContext(NonCancellable) {
            for (attempt in 0 until 2) {
                try {
                    api.deleteTorrent(torrentId)
                    return@withContext
                } catch (e: Exception) {
                    delay(500)
                }
            }
        }
    }

    private companion object {
        // Conclusive results wanted per checkCacheStatus call. Real-Debrid
        // caps active downloads per account (reported as low as 6 in 2024;
        // not re-verified), so stay conservative.
        const val MAX_PROBES = 6

        // Hard cap on candidates tried, so blocked/dead hashes cannot make
        // the probe run forever. Failed adds cost one request and leave
        // nothing in the account.
        const val MAX_ATTEMPTS = 12
        const val PROBE_CONCURRENCY = 2

        const val MAGNET_CONVERSION_POLLS = 5
        const val MAGNET_CONVERSION_DELAY_MS = 1000L

        const val SETTLE_POLLS = 5
        const val SETTLE_DELAY_MS = 500L

        val SETTLING_STATUSES = setOf(
            "magnet_conversion",
            "waiting_files_selection",
            "queued",
            "downloading"
        )

        // Real-Debrid error codes (https://api.real-debrid.com/)
        const val ERR_INFRINGING_FILE = 35
        // 8 bad token, 9 permission denied, 10/11 two-factor, 14 account locked
        val AUTH_ERROR_CODES = setOf(8, 9, 10, 11, 14)
        // 5 slow down, 21 too many active downloads, 22 IP not allowed,
        // 23 traffic exhausted, 34 too many requests, 36 fair usage limit
        val STOP_ERROR_CODES = setOf(5, 21, 22, 23, 34, 36)

        val ERROR_CODE_REGEX = Regex("\"error_code\"\\s*:\\s*(\\d+)")
    }
}