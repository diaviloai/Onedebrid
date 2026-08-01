package com.onedebrid.app.data.repository

import com.onedebrid.app.data.local.dao.SearchHistoryDao
import com.onedebrid.app.data.local.entity.SearchHistoryEntity
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val dispatchers: CoroutineDispatchers
) : SearchRepository {

    override fun observeSearchHistory(profileId: String): Flow<List<String>> =
    searchHistoryDao.observeSearchHistory(profileId)
        .map { entities -> entities.map { it.query } }
        .flowOn(dispatchers.io)

    /**
     * Re-searching an existing query moves it to the top of the list
     * by updating searchedAt via REPLACE. This matches the expected UX
     * behaviour — the most recently used query appears first.
     */
    override suspend fun addSearchQuery(
        query: String,
        profileId: String
    ): Unit = withContext(dispatchers.io) {
        val entity = SearchHistoryEntity(
            profileId = profileId,
            query = query,
            searchedAt = System.currentTimeMillis()
        )
        searchHistoryDao.upsertQuery(entity)
    }

    override suspend fun removeSearchQuery(
        query: String,
        profileId: String
    ): Unit = withContext(dispatchers.io) {
        searchHistoryDao.removeQuery(profileId, query)
    }

    override suspend fun clearSearchHistory(
        profileId: String
    ): Unit = withContext(dispatchers.io) {
        searchHistoryDao.clearHistoryForProfile(profileId)
    }
}