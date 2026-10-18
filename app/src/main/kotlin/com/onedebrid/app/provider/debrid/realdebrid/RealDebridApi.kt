package com.onedebrid.app.provider.debrid.realdebrid

import kotlinx.serialization.json.JsonElement
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RealDebridApi {

    @GET("user")
    suspend fun getUser(): RdUserDto

    /**
     * DEAD ENDPOINT: Real-Debrid disabled this in Nov 2024 and it now returns
     * {"error":"disabled_endpoint","error_code":37} (HTTP 403). Kept only so
     * existing references compile. RealDebridProvider.checkCacheStatus no
     * longer calls it; cache detection uses add -> select -> inspect -> delete
     * probing instead.
     */
    @GET("torrents/instantAvailability/{hashes}")
    suspend fun checkInstantAvailability(
        @Path("hashes") slashSeparatedHashes: String
    ): JsonElement

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Field("magnet") magnet: String
    ): RdAddMagnetDto

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Path("id") torrentId: String
    ): RdTorrentInfoDto

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Path("id") torrentId: String,
        @Field("files") files: String = "all"
    )

    /**
     * Removes a torrent from the user's Real-Debrid account. Returns HTTP 204
     * (no body) on success. Used by the cache probe so a probed torrent never
     * lingers in the account or keeps occupying an active-download slot.
     */
    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Path("id") torrentId: String
    )

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Field("link") link: String
    ): RdUnrestrictUrlDto
}