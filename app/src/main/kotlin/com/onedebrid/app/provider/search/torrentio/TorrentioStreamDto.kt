package com.onedebrid.app.provider.search.torrentio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TorrentioStreamResponseDto(
    val streams: List<TorrentioStreamDto> = emptyList()
)

@Serializable
data class TorrentioStreamDto(
    val name: String? = null,
    val title: String? = null,
    @SerialName("infoHash") val infoHash: String? = null,
    @SerialName("fileIdx") val fileIdx: Int? = null,
    val behaviorHints: TorrentioBehaviorHintsDto? = null
)

@Serializable
data class TorrentioBehaviorHintsDto(
    @SerialName("bingeGroup") val bingeGroup: String? = null
)
