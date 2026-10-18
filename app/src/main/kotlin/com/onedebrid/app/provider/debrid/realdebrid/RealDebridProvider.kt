package com.onedebrid.app.provider.debrid.realdebrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.serialization.SerializationException
import okio.IOException
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
        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
        }
    }

    override suspend fun resolveStream(candidate: StreamCandidate): ProviderResult<StreamSource> {
        return try {
            val magnetOrLink = candidate.magnetUrl

            // 1. If Torrentio returned a direct HTTP link (e.g. already resolved by a Debrid service)
            if (magnetOrLink != null && magnetOrLink.startsWith("http")) {
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
            }

            // 2. Otherwise, it's a magnet link or hash. Add it to Real-Debrid.
            val magnet = if (magnetOrLink != null && magnetOrLink.startsWith("magnet:")) {
                magnetOrLink
            } else {
                "magnet:?xt=urn:btih:${candidate.hash}"
            }
            
            val addResponse = api.addMagnet(magnet)
            val torrentId = addResponse.id ?: return ProviderError.NotFound.asFailure()

            // 3. Get Torrent Info
            var infoResponse = api.getTorrentInfo(torrentId)
            
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
                
                // Refresh info to get the generated links
                infoResponse = api.getTorrentInfo(torrentId)
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

        } catch (e: HttpException) {
            e.toProviderError().asFailure()
        } catch (e: IOException) {
            ProviderError.NetworkError.asFailure()
        } catch (e: SerializationException) {
            ProviderError.ParsingError(cause = e).asFailure()
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