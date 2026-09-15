package com.onedebrid.app.provider.debrid.realdebrid

import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.error.asFailure
import com.onedebrid.app.domain.error.asSuccess
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.provider.debrid.AccountInfo
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.serialization.SerializationException
import okio.IOException
import retrofit2.HttpException
import javax.inject.Inject

class RealDebridProvider @Inject constructor(
    private val api: RealDebridApi
) : DebridProvider {

    override val id: String = "real_debrid"
    override val displayName: String = "Real-Debrid"

    override suspend fun verifyAccount(): ProviderResult<AccountInfo> = try {
        val user = api.getUser()
        val isActive = user.type == "premium"
        AccountInfo(
            username = user.username,
            isActive = isActive
        ).asSuccess()
    } catch (e: HttpException) {
        e.toProviderError().asFailure()
    } catch (e: IOException) {
        ProviderError.NetworkError.asFailure()
    } catch (e: SerializationException) {
        ProviderError.ParsingError(cause = e).asFailure()
    }

    override suspend fun checkCache(
        hashes: List<String>
    ): ProviderResult<Map<String, Boolean>> {
        if (hashes.isEmpty()) return emptyMap<String, Boolean>().asSuccess()

        val pathParam = hashes.joinToString("/")
        return try {
            val response = api.checkInstantAvailability(pathParam)
            val resultMap = hashes.associateWith { hash ->
                val lowerHash = hash.lowercase()
                val hosterMap = response[lowerHash]
                // Hashes are cached if any hoster key contains non-empty file lists
                hosterMap?.values?.any { list -> list.isNotEmpty() } == true
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

    override suspend fun resolveStream(hash: String): ProviderResult<StreamSource> = try {
        val magnet = "magnet:?xt=urn:btih:$hash"
        val addedMagnet = api.addMagnet(magnet)
        
        // Select all files to trigger link generation for cached items
        api.selectFiles(addedMagnet.id, "all")
        
        val torrentInfo = api.getTorrentInfo(addedMagnet.id)
        val firstLink = torrentInfo.links.firstOrNull()
            ?: return ProviderError.NotFound.asFailure()

        val unrestrictResult = api.unrestrictLink(firstLink)

        StreamSource(
            id = unrestrictResult.id,
            mediaId = hash,
            url = unrestrictResult.link,
            quality = VideoQuality.UNKNOWN,
            fileSizeBytes = unrestrictResult.filesize,
            fileName = unrestrictResult.filename,
            isCached = true
        ).asSuccess()
    } catch (e: HttpException) {
        e.toProviderError().asFailure()
    } catch (e: IOException) {
        ProviderError.NetworkError.asFailure()
    } catch (e: SerializationException) {
        ProviderError.ParsingError(cause = e).asFailure()
    }

    private fun HttpException.toProviderError(): ProviderError = when (code()) {
        401, 403 -> ProviderError.AuthenticationFailed
        404 -> ProviderError.NotFound
        429 -> ProviderError.RateLimited(retryAfterSeconds = null)
        in 500..599 -> ProviderError.ServiceUnavailable
        else -> ProviderError.ParsingError(cause = this)
    }
}
