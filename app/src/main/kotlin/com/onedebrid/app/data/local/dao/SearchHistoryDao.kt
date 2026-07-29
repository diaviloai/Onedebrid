package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    // --- Observation ---

    /**
     * Observes the search history for a profile, most recent first.
     * Drives the suggestions list shown below the search bar when the
     * user focuses the search field.
     *
     * limit keeps the list short — history is for quick re-use, not
     * a full archive. 50 is a reasonable ceiling; the caller can reduce it.
     */
    @Query("""
        SELECT * FROM search_history
        WHERE profileId = :profileId
        ORDER BY searchedAt DESC
        LIMIT :limit
    """)
    fun observeSearchHistory(
        profileId: String,
        limit: Int = 50
    ): Flow<List<SearchHistoryEntity>>

    // --- Writes ---

    /**
     * Inserts a search query. IGNORE on conflict means if the exact
     * (profileId, query) pair already exists, the existing row is left
     * untouched rather than replaced.
     *
     * This preserves the original searchedAt timestamp for the first time
     * the query was used. If you want re-searches to bump the timestamp
     * to the top of the list, use upsertQuery() below instead.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuery(entry: SearchHistoryEntity)

    /**
     * Inserts or replaces a search query. REPLACE on conflict overwrites
     * the existing row, which updates searchedAt to now.
     *
     * Use this when re-searching an existing term should move it back to
     * the top of the history list. The repository decides which behaviour
     * to use — both functions are available.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuery(entry: SearchHistoryEntity)

    /**
     * Removes a specific query from history.
     * Used when the user swipes away or taps delete on a history item.
     */
    @Query("""
        DELETE FROM search_history
        WHERE profileId = :profileId AND query = :query
    """)
    suspend fun removeQuery(profileId: String, query: String)

    /**
     * Clears all search history for a profile.
     * Called from Settings when the user chooses to clear search history.
     */
    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun clearHistoryForProfile(profileId: String)
}