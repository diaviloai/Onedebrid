package com.onedebrid.app.domain.model

import kotlinx.serialization.Serializable

/**

 * Represents a single episode of a TV show.
 *
 * Episode always belongs to a parent Media of type TV_SHOW. It carries enough
 * information for playback, subtitle matching, and Continue Watching without
 * requiring the parent Media to be loaded first.
 *
 * Like Media, fields are nullable where data may not be available immediately.
 * An Episode can be created from minimal information and enriched later by the
 * Metadata system.
 */
@Serializable
data class Episode(
    val id: String,
    val mediaId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
    val airDate: String? = null,
    val runtime: Int? = null
)