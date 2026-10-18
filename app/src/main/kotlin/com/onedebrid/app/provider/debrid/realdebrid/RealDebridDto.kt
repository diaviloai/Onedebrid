package com.onedebrid.app.provider.debrid.realdebrid

import kotlinx.serialization.Serializable

@Serializable
data class RdUserDto(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val type: String? = null,
    val expiration: String? = null
)

@Serializable
data class RdAddMagnetDto(
    val id: String? = null,
    val uri: String? = null
)

@Serializable
data class RdTorrentInfoDto(
    val id: String? = null,
    val filename: String? = null,
    val status: String? = null,
    val bytes: Long? = null,
    val links: List<String>? = null,
    val files: List<RdFileDto>? = null
)

@Serializable
data class RdFileDto(
    val id: Int? = null,
    val path: String? = null,
    val bytes: Long? = null,
    val selected: Int? = null
)

@Serializable
data class RdUnrestrictUrlDto(
    val id: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val filesize: Long? = null,
    val link: String? = null,
    val host: String? = null
)

@Serializable
data class RdInstantFileDto(
    val filename: String? = null,
    val filesize: Long? = null
)

typealias RdInstantAvailabilityDto = Map<String, Map<String, List<Map<String, RdInstantFileDto>>>>