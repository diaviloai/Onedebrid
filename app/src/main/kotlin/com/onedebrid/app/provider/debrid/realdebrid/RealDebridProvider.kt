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
        
        // RD expects hashes separated by slashes: hash1/hash2/hash3
        val hashes = candidates.joinToString("/") { it.hash }
        
        return try {
            val response = api.checkInstantAvailability(hashes)
            val resultMap = mutableMapOf<String, Boolean>()
            
            candidates.forEach { candidate ->
                // RD returns a map of hashes. If the hash exists and has hosters, it's cached.
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
            // 1. Add Magnet
            val magnet = candidate.magnetUrl ?: "magnet:?xt=urn:btih:${candidate.hash}"
            val addResponse = api.addMagnet(magnet)
            val torrentId = addResponse.id

            // 2. Get Torrent Info to find files
            val infoResponse = api.getTorrentInfo(torrentId)
            
            // 3. Select Files (Find the largest video file)
            val videoFiles = infoResponse.files.filter { 
                it.path.endsWith(".mp4") || it.path.endsWith(".mkv") || it.path.endsWith(".avi") 
            }
            
            val selectedFile = videoFiles.maxByOrNull { it.bytes } 
                ?: return ProviderError.NotFound.asFailure()

            api.selectFiles(torrentId, selectedFile.id.toString())

            // 4. Get the updated info to get the generated link
            val updatedInfo = api.getTorrentInfo(torrentId)
            val link = updatedInfo.links.firstOrNull() 
                ?: return ProviderError.NotFound.asFailure()

            // 5. Unrestrict the link
            val unrestrictResponse = api.unrestrictLink(link)

            StreamSource(
                id = unrestrictResponse.id,
                mediaId = "", // This will be populated by the Repository layer
                url = unrestrictResponse.link,
                quality = candidate.quality,
                fileSizeBytes = unrestrictResponse.filesize ?: selectedFile.bytes,
                fileName = unrestrictResponse.filename ?: selectedFile.path,
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