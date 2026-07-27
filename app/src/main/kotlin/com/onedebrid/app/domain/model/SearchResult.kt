package com.onedebrid.app.domain.model

/**
 * Represents a single result returned by the Search system.
 *
 * SearchResult pairs a Media object with metadata about the result itself —
 * where it came from, how confident we are in it, and what stream candidates
 * were found alongside it.
 *
 * Media carries what the content is. SearchResult carries what we know about
 * finding it.
 *
 * The Search system produces SearchResults. The UI consumes them. Playback
 * consumes the Media and StreamCandidates within them.
 */
data class SearchResult(
    val media: Media,
    val candidates: List<StreamCandidate> = emptyList(),
    val sourceProvider: String,
    val relevanceScore: Float = 0f
)

/**
 * Represents an unresolved stream candidate found during search.
 *
 * A StreamCandidate is a torrent or magnet link that has not yet been sent
 * through Real-Debrid. It becomes a StreamSource only after the Debrid system
 * confirms it is cached and resolves it to a direct URL.
 *
 * title: The raw torrent or release title as returned by the search provider.
 * hash: The torrent info hash used for debrid cache checking.
 * magnetUrl: The full magnet link, used if hash alone is insufficient.
 * sizeBytes: File size in bytes if available from the search provider.
 * seeders: Seeder count where available. Not meaningful after debrid resolution.
 * quality: Quality parsed from the release title. May be UNKNOWN.
 */
data class StreamCandidate(
    val title: String,
    val hash: String? = null,
    val magnetUrl: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val quality: VideoQuality = VideoQuality.UNKNOWN
)