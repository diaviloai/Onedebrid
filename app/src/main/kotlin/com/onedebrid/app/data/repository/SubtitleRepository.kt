package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.model.SubtitleTrack
import com.onedebrid.app.provider.subtitle.SubtitleQuery

/**
 * Repository for subtitle discovery and retrieval.
 *
 * Abstracts subtitle providers (e.g. OpenSubtitles) behind a single
 * interface. Handles caching of subtitle search results internally.
 */
interface SubtitleRepository {

    /**
     * Search for available subtitles matching the given query.
     *
     * Results may come from cache if a recent search for the same
     * media and language was already performed.
     *
     * Returns an empty list (not a failure) if no subtitles are found.
     */
    suspend fun searchSubtitles(query: SubtitleQuery): RepositoryResult<List<SubtitleTrack>>

    /**
     * Download a specific subtitle track so it is ready for playback.
     *
     * The returned SubtitleTrack will have a local file path populated.
     * Before download, the localPath on the track is null.
     */
    suspend fun downloadSubtitle(track: SubtitleTrack): RepositoryResult<SubtitleTrack>
}