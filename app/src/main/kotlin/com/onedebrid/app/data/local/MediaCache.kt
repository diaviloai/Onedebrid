package com.onedebrid.app.data.local

import com.onedebrid.app.data.local.dao.CacheEntryDao
import com.onedebrid.app.data.local.entity.CacheEntryEntity
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache for Media and Episode-list lookups, backed by the generic
 * CacheEntryDao/CacheEntryEntity table (Session 25).
 *
 * This is the "Media cache/lookup layer" referenced throughout
 * currentsprint.md's Next Steps and several files' doc comments
 * (PendingPlaybackHolder.kt, HomeScreen.kt, NavGraph.kt). Those comments
 * predate this class and describe the gap it fills; they are not yet
 * updated to reflect that the gap is closed, since MediaRepositoryImpl is
 * the caller that actually wires this in.
 *
 * Key convention follows CacheEntryEntity's own doc comment exactly:
 *   cacheType "metadata", key "metadata:<mediaId>" for a single Media
 *   cacheType "episodes", key "episodes:<mediaId>" for an episode list
 * Using a distinct cacheType for episodes (rather than reusing "metadata")
 * means CacheEntryDao.clearType()/getEntriesForType() can operate on either
 * independently if that's ever needed.
 *
 * TTL is a flat 7 days for both, per Dia's confirmation this session.
 * Metadata (title, artwork, episode lists) changes rarely enough that a
 * week-long cache is a reasonable default; this is a starting assumption,
 * not a value derived from any specific requirement, and can be revisited
 * if it causes staleness complaints once real metadata providers exist.
 *
 * Read failures (a malformed or unreadable cache entry) are treated as a
 * cache miss, not a thrown exception — the caller falls through to the
 * network/provider path either way, so surfacing a deserialization error
 * here would only add a failure mode without a corresponding benefit.
 */
@Singleton
class MediaCache @Inject constructor(
    private val cacheEntryDao: CacheEntryDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getMedia(mediaId: String): Media? {
        val entry = cacheEntryDao.getEntry(
            cacheType = CACHE_TYPE_MEDIA,
            cacheKey = mediaKey(mediaId),
            now = System.currentTimeMillis()
        ) ?: return null
        return runCatching { json.decodeFromString<Media>(entry.data) }.getOrNull()
    }

    suspend fun putMedia(media: Media) {
        val now = System.currentTimeMillis()
        cacheEntryDao.upsertEntry(
            CacheEntryEntity(
                key = mediaKey(media.id),
                cacheType = CACHE_TYPE_MEDIA,
                data = json.encodeToString(media),
                cachedAt = now,
                expiresAt = now + TTL_MS
            )
        )
    }

    suspend fun getEpisodes(mediaId: String): List<Episode>? {
        val entry = cacheEntryDao.getEntry(
            cacheType = CACHE_TYPE_EPISODES,
            cacheKey = episodesKey(mediaId),
            now = System.currentTimeMillis()
        ) ?: return null
        return runCatching { json.decodeFromString<List<Episode>>(entry.data) }.getOrNull()
    }

    suspend fun putEpisodes(mediaId: String, episodes: List<Episode>) {
        val now = System.currentTimeMillis()
        cacheEntryDao.upsertEntry(
            CacheEntryEntity(
                key = episodesKey(mediaId),
                cacheType = CACHE_TYPE_EPISODES,
                data = json.encodeToString(episodes),
                cachedAt = now,
                expiresAt = now + TTL_MS
            )
        )
    }

    private fun mediaKey(mediaId: String) = "$CACHE_TYPE_MEDIA:$mediaId"

    private fun episodesKey(mediaId: String) = "$CACHE_TYPE_EPISODES:$mediaId"

    private companion object {
        const val CACHE_TYPE_MEDIA = "metadata"
        const val CACHE_TYPE_EPISODES = "episodes"
        const val TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
}