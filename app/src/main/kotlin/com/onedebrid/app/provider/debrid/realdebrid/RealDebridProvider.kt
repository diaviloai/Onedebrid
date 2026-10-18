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
import retrofit2.HttpException
import javax.inject.Inject

class RealDebridProvider @Inject constructor(
    private val api: RealDebridApi
) : DebridProvider {

    override val id: String = "realdebrid"
    override val displayName: String = "Real-Debrid"

    override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): ProviderResult<Map<String, Boolean>> {
        if (candidates.isEmpty()) return emptyMap<String, Boolean>().asSuccess()
        
        val hashes = candidates.joinToString("/") { it.hash }
        
        return try {
            val response = api.checkInstantAvailability(hashes)
            val resultMap = mutableMapOf<String, Boolean>()
            
            candidates.forEach { candidate ->
                val hashData = response[candidate.hash.lowercase()]
                val isCached = hashData?.isNotEmpty() == true
                resultMap[candidate.hash] = isCached
            }
            
            resultMap.asSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // If cache check fails (e.g. network error, 401), gracefully return empty map
            // so the app doesn't crash and just assumes nothing is cached.
            if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                return ProviderError.AuthenticationFailed.asFailure()
            }
            emptyMap<String, Boolean>().asSuccess()
        }
    }

    override suspend fun resolveStream(candidate: StreamCandidate): ProviderResult<StreamSource> {
        return try {
            val magnetOrLink = candidate.magnetUrl

            // 1. If Torrentio returned a direct HTTP link
            if (magnetOrLink != null && magnetOrLink.startsWith("http")) {
                if (magnetOrLink.contains("real-debrid.com")) {
                    // It's an RD link, unrestrict it
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
                    // It's a direct link from another host (e.g. web seed), just play it directly!
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

            // 2. Otherwise, it's a magnet link or hash. Add it to Real-Debrid.
            val magnet = if (magnetOrLink != null && magnetOrLink.startsWith("magnet:")) {
                magnetOrLink
            } else {
                "magnet:?xt=urn:btih:${candidate.hash}"
            }
            
            val addResponse = api.addMagnet(magnet)
            val torrentId = addResponse.id ?: return ProviderError.NotFound.asFailure()

            // 3. Get Torrent Info and wait for magnet conversion
            var infoResponse = api.getTorrentInfo(torrentId)
            var attempts = 0
            while (infoResponse.status == "magnet_conversion" && attempts < 10) {
                delay(1000)
                infoResponse = api.getTorrentInfo(torrentId)
                attempts++
            }
            
            // 4. Select Files ONLY if Real-Debrid is waiting for us to do so.
            if (infoResponse.status == "waiting_files_selection") {
                val allFiles = infoResponse.files ?: emptyList()
                val videoFiles = allFiles.filter { 
                    val path = it.path?.lowercase() ?: ""
                    path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") 
                }
                
                val selectedFile = videoFiles.maxByOrNull { it.bytes ?: 0L } 
                    ?: return ProviderError.NotFound.asFailure()

                val fileId = selectedFile.id ?: return ProviderError.NotFound.asFailure()
                api.selectFiles(torrentId, fileId.toString())
                
                // Wait for links to be generated
                attempts = 0
                infoResponse = api.getTorrentInfo(torrentId)
                while (infoResponse.links.isNullOrEmpty() && attempts < 10) {
                    delay(1000)
                    infoResponse = api.getTorrentInfo(torrentId)
                    attempts++
                }
            }

            // 5. Get the generated link
            val links = infoResponse.links ?: emptyList()
            val link = links.firstOrNull() 
                ?: return ProviderError.NotFound.asFailure()

            // 6. Unrestrict the link
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
            
            // Catch ALL other exceptions (400, 404, 422, SerializationException, IOException)
            // and return NotFound so the UI gracefully shows "No cached stream available"
            // instead of crashing with "Unexpected error".
            ProviderError.NotFound.asFailure()
        }
    }
}