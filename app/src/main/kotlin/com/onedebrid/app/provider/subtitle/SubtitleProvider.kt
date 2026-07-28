package com.onedebrid.app.provider.subtitle

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.SubtitleTrack

/**
 * Contract for all subtitle service integrations (e.g. OpenSubtitles).
 *
 * Responsible for discovering and fetching subtitle files. Subtitle
 * support is a first-class feature. Multiple providers may be queried
 * in parallel with the fastest valid response used.
 *
 * Defined in Provider Architecture v0.1.
 */
interface SubtitleProvider {

    val id: String
    val displayName: String

    /**
     * Searches for subtitles matching the given query.
     *
     * [query] describes what subtitle is needed. Providers use
     * whichever fields are relevant to their search API.
     *
     * Returns a list of available [SubtitleTrack] objects, each
     * representing a downloadable subtitle file. An empty list is
     * a valid success — it means no subtitles were found, not that
     * the search failed.
     */
    suspend fun searchSubtitles(query: SubtitleQuery): ProviderResult<List<SubtitleTrack>>

    /**
     * Downloads and parses a subtitle file identified by its provider URL.
     *
     * [downloadUrl] comes from a [SubtitleTrack] returned by [searchSubtitles].
     *
     * Returns a [SubtitleTrack] with its content populated, ready to
     * attach to the player.
     */
    suspend fun downloadSubtitle(downloadUrl: String): ProviderResult<SubtitleTrack>
}

/**
 * Describes the subtitle being searched for.
 *
 * Providers use whichever fields their API supports and ignore the rest.
 * No field is required — a provider receiving only a title should do
 * its best with what it has.
 */
data class SubtitleQuery(
    val mediaTitle: String,
    val year: Int? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val languageCodes: List<String> = emptyList(),  // BCP-47, e.g. "en", "fr"
    val fileHash: String? = null  // some providers match by file hash for accuracy
)