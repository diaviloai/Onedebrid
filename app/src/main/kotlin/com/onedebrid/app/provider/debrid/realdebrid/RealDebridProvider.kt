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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
     * Probing stops early on any HTTP error (rate limit, active-download
     * cap, auth), keeping whatever was already determined.
     */
    override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): ProviderResult<Map<String, Boolean>> {
        if (candidates.isEmpty()) return emptyMap<String, Boolean>().asSuccess()

        val targets = selectProbeTargets(candidates)
        val results = ConcurrentHashMap<String, Boolean>()
        val stopProbing = AtomicBoolean(false)
        val authFailed = AtomicBoolean(false)
        val gate = Semaphore(PROBE_CONCURRENCY)

        coroutineScope {
            targets.map { candidate ->
                async {
                    gate.withPermit {
                        if (stopProbing.get()) return@withPermit
                        try {
                            val cached = probeCacheStatus(candidate)
                            if (cached != null) results[candidate.hash] = cached
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HttpException) {
                            stopProbing.set(true)
                            if (e.code() == 401 || e.code() == 403) authFailed.set(true)
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
     * Picks which candidates are worth probing: best quality first, then
     * most seeders, de-duplicated by hash, capped at [MAX_PROBES] so we
     * never hammer the account or exhaust active-download slots.
     */
    private fun selectProbeTargets(candidates: List<StreamCandidate>): List<StreamCandidate> =
        candidates
            .distinctBy { it.hash.lowercase() }
            .sortedWith(
                compareByDescending<StreamCandidate> { qualityRank(it.quality) }
                    .thenByDescending { it.seeders }
            )
            .take(MAX_PROBES)

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
                "queued", "downloading" -> false
                else -> null
            }
        } finally {
            deleteProbeTorrent(torrentId)
        }
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
        // Max torrents probed per checkCacheStatus call. Real-Debrid caps
        // active downloads per account (reported as low as 6 in 2024; not
        // re-verified), so stay conservative.
        const val MAX_PROBES = 6
        const val PROBE_CONCURRENCY = 2

        const val MAGNET_CONVERSION_POLLS = 5
        const val MAGNET_CONVERSION_DELAY_MS = 1000L

        const val SETTLE_POLLS = 3
        const val SETTLE_DELAY_MS = 600L

        val SETTLING_STATUSES = setOf(
            "magnet_conversion",
            "waiting_files_selection",
            "queued",
            "downloading"
        )
    }
}