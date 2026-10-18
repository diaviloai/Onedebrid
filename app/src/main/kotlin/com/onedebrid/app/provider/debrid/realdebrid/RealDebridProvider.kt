package com.onedebrid.app.provider.debrid.realdebrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException
import javax.inject.Inject

class RealDebridProvider @Inject constructor(
    private val api: RealDebridApi
) : DebridProvider {

    override val id: String = "realdebrid"
    override val displayName: String = "Real-Debrid"

    override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): ProviderResult<Map<String, Boolean>> {
        if (candidates.isEmpty()) return emptyMap<String, Boolean>().asSuccess()
        
        // RD allows max 100 hashes per request. Take the first 100 to be safe.
        val hashes = candidates.take(100).joinToString("/") { it.hash }
        
        return try {
            val response = api.checkInstantAvailability(hashes)
            val resultMap = mutableMapOf<String, Boolean>()
            
            if (response is JsonObject) {
                candidates.forEach { candidate ->
                    val hash = candidate.hash.lowercase()
                    val hashElement = response[hash]
                    // If it's a JsonObject, it has hosters (cached). If it's a JsonArray, it's empty (uncached).
                    val isCached = hashElement is JsonObject && hashElement.isNotEmpty()
                    resultMap[candidate.hash] = isCached
                }
            }
            
            resultMap.asSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                return ProviderError.AuthenticationFailed.asFailure()
            }
            emptyMap<String, Boolean>().asSuccess()
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
}