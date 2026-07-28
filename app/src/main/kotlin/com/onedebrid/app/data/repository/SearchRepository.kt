package com.onedebrid.app.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for search history persistence.
 *
 * Owned by the Search System. Stores and retrieves the user's
 * previous search queries. Does not execute searches — that
 * responsibility belongs to the Search Use Case and SearchProvider.
 *
 * Search history is user-configurable and may be cleared at any time,
 * as required by the database design specification.
 */
interface SearchRepository {

    /**
     * Observe the current search history.
     *
     * Emits a new list whenever history changes.
     * Ordered by most recent first.
     * Respects the active profile — history is per-profile.
     */
    fun observeSearchHistory(): Flow<List<String>>

    /**
     * Add a query to search history.
     *
     * Called after a search is successfully executed.
     * If the query already exists in history, it is moved to the
     * top rather than duplicated.
     *
     * [profileId] ensures history is stored against the correct profile.
     */
    suspend fun addSearchQuery(query: String, profileId: String)

    /**
     * Remove a single query from search history.
     *
     * Called when the user explicitly deletes a history item.
     */
    suspend fun removeSearchQuery(query: String, profileId: String)

    /**
     * Clear all search history for a profile.
     *
     * User-initiated. Should complete immediately from the user's
     * perspective.
     */
    suspend fun clearSearchHistory(profileId: String)
}