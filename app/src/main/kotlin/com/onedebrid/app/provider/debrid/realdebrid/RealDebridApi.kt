package com.onedebrid.app.provider.debrid.realdebrid

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RealDebridApi {

    @GET("user")
    suspend fun getUser(): RdUserDto

    @GET("torrents/instantAvailability/{hashes}")
    suspend fun checkInstantAvailability(
        @Path("hashes") slashSeparatedHashes: String
    ): RdInstantAvailabilityDto

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

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Field("link") link: String
    ): RdUnrestrictUrlDto
}
