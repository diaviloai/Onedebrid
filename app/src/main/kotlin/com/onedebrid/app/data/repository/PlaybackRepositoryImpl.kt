package com.onedebrid.app.data.repository

import com.onedebrid.app.data.local.dao.ContinueWatchingDao
import com.onedebrid.app.data.local.dao.RecentlyPlayedDao
import com.onedebrid.app.data.local.entity.ContinueWatchingEntity
import com.onedebrid.app.data.local.entity.RecentlyPlayedEntity
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.WatchedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val continueWatchingDao: ContinueWatchingDao,
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val dispatchers: CoroutineDispatchers
) : PlaybackRepository {

    // --- Continue Watching ---

    override fun observeContinueWatching(profileId: String): Flow<List<WatchedItem>> =
        continueWatchingDao.observeContinueWatching(profileId)
            .map { entities -> entities.map { it.toWatchedItem() } }
            .flowOn(dispatchers.io)

    override suspend fun removeFromContinueWatching(
        profileId: String,
        mediaId: String
    ): Unit = withContext(dispatchers.io) {
        continueWatchingDao.removeEntry(profileId, mediaId)
    }

    // --- Playback Progress ---

    override suspend fun saveProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long
    ): Unit = withContext(dispatchers.io) {
        val entity = ContinueWatchingEntity(
            profileId = profileId,
            mediaId = mediaId,
            episodeId = episodeId,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = false
        )
        continueWatchingDao.upsertProgress(entity)
    }

    override suspend fun getProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Long?> = withContext(dispatchers.io) {
        runCatching {
            val entity = continueWatchingDao.getProgressForMedia(profileId, mediaId)
            RepositoryResult.Success(entity?.positionMs)
        }.getOrElse { cause ->
            RepositoryResult.Failure(AppError.LocalStorageError(cause))
        }
    }

    override suspend fun markAsCompleted(
        profileId: String,
        mediaId: String
    ): Unit = withContext(dispatchers.io) {
        continueWatchingDao.markAsCompleted(profileId, mediaId)
    }

    // --- Recently Played ---

    override fun observeRecentlyPlayed(profileId: String): Flow<List<WatchedItem>> =
        recentlyPlayedDao.observeRecentlyPlayed(profileId)
            .map { entities -> entities.map { it.toWatchedItem() } }
            .flowOn(dispatchers.io)

    override suspend fun recordPlayed(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Unit = withContext(dispatchers.io) {
        val entity = RecentlyPlayedEntity(
            profileId = profileId,
            mediaId = mediaId,
            episodeId = episodeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            lastPlayedAt = System.currentTimeMillis()
        )
        recentlyPlayedDao.upsertEntry(entity)
    }

    override suspend fun clearHistory(profileId: String): Unit =
        withContext(dispatchers.io) {
            continueWatchingDao.clearAllForProfile(profileId)
            recentlyPlayedDao.clearAllForProfile(profileId)
        }

    // --- Mapping ---

    private fun ContinueWatchingEntity.toWatchedItem() = WatchedItem(
        mediaId = mediaId,
        episodeId = episodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        positionMs = positionMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        lastInteractedAt = lastWatchedAt
    )

    private fun RecentlyPlayedEntity.toWatchedItem() = WatchedItem(
        mediaId = mediaId,
        episodeId = episodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        positionMs = null,
        durationMs = null,
        isCompleted = null,
        lastInteractedAt = lastPlayedAt
    )
}