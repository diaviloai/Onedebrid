package com.onedebrid.app.provider.search.torrentio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Network DTOs for the Torrentio addon API.
 *
 * These mirror Torrentio's response shape exactly (which itself follows
 * the general Stremio addon protocol for the `stream` resource — see
 * https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md).
 *
 * Per Provider Architecture v0.1 / Technical Standards v0.1: DTOs stay
 * inside the provider package and are mapped to domain models
 * (StreamCandidate) before crossing into the rest of the app. Nothing in
 * this file should be referenced outside provider/search/torrentio/.
 *
 * Session 29 note on infoHash: an observed real-world issue
 * (rivenmedia/riven#1342, Jan 2026) shows some Torrentio responses omit
 * `infoHash` and instead only provide a debrid `resolve` URL containing
 * the hash embedded in its path. This DTO keeps [infoHash] nullable to
 * reflect that — TorrentioSearchProvider.kt is responsible for the
 * fallback extraction, not this file. This DTO only describes what the
 * wire format can contain, not how to recover from its absence.
 */
@Serializable
data class TorrentioStreamResponseDto(
    val streams: List<TorrentioStreamDto> = emptyList()
)

@Serializable
data class TorrentioStreamDto(
    val name: String? = null,
    val title: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val url: String? = null,
    val sources: List<String>? = null,
    @SerialName("behaviorHints")
    val behaviorHints: TorrentioBehaviorHintsDto? = null
)

@Serializable
data class TorrentioBehaviorHintsDto(
    @SerialName("bingeGroup")
    val bingeGroup: String? = null,
    @SerialName("videoSize")
    val videoSize: Long? = null,
    val filename: String? = null
)