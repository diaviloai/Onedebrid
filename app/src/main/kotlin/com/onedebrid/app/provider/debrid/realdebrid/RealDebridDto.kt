package com.onedebrid.app.provider.debrid.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RdUserDto(
    val id: Long,
    val username: String,
    val email: String? = null,
    val type: String, // "premium" or "free"
    val expiration: String? = null
)

@Serializable
data class RdAddMagnetDto(
    val id: String,
    val uri: String? = null
)

@Serializable
data class RdTorrentInfoDto(
    val id: String,
    val filename: String,
    val status: String, // e.g. "downloaded", "waiting_files_selection"
    val bytes: Long? = null,
    val links: List<String> = emptyList(),
    val files: List<RdFileDto> = emptyList()
)

@Serializable
data class RdFileDto(
    val id: Int,
    val path: String,
    val bytes: Long,
    val selected: Int
)

@Serializable
data class RdUnrestrictUrlDto(
    val id: String,
    val filename: String,
    val mimeType: String? = null,
    val filesize: Long? = null,
    val link: String,
    val host: String? = null
)

// Instant availability response: Map<InfoHash, Map<Hoster, List<Map<FileId, FileInfo>>>>
typealias RdInstantAvailabilityDto = Map<String, Map<String, List<Map<String, RdFileDto>>>>
