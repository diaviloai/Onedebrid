package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.CacheEntryEntity

@Dao
interface CacheEntryDao {

    // --- Reads ---

    /**
     * Returns a single cache entry by type and key.
     * Returns null if no entry exists or if the entry has expired.
     *
     * Expiry is checked here at read time — an expired entry is treated
     * as a cache miss even if the row still exists. This means stale rows
     * are invisible to callers without requiring immediate deletion.
     * Actual row cleanup happens via pruneExpired().
     */
    @Query("""
        SELECT * FROM cache_entries
        WHERE cacheType = :cacheType AND cacheKey = :cacheKey
        AND (expiresAt IS NULL OR expiresAt > :now)
        LIMIT 1
    """)
    suspend fun getEntry(
        cacheType: String,
        cacheKey: String,
        now: Long
    ): CacheEntryEntity?

    /**
     * Returns all valid (non-expired) entries for a given cache type.
     * Intended for diagnostic or management use — normal cache access
     * goes through getEntry() with a specific key.
     */
    @Query("""
        SELECT * FROM cache_entries
        WHERE cacheType = :cacheType
        AND (expiresAt IS NULL OR expiresAt > :now)
    """)
    suspend fun getEntriesForType(
        cacheType: String,
        now: Long
    ): List<CacheEntryEntity>

    // --- Writes ---

    /**
     * Inserts or replaces a cache entry.
     * REPLACE on conflict means writing a new value for an existing
     * (cacheType, cacheKey) pair updates it in place, refreshing
     * the data and the expiresAt timestamp together.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: CacheEntryEntity)

    // --- Deletion ---

    /**
     * Removes a specific cache entry by type and key.
     * Used when the Cache System needs to invalidate a single entry
     * — for example, after a metadata refresh replaces a cached value.
     */
    @Query("""
        DELETE FROM cache_entries
        WHERE cacheType = :cacheType AND cacheKey = :cacheKey
    """)
    suspend fun deleteEntry(cacheType: String, cacheKey: String)

    /**
     * Deletes all expired entries across all cache types.
     * The Cache System calls this periodically to reclaim storage.
     * Passing now as a parameter keeps this testable — tests can
     * control what "now" means without mocking system time.
     */
    @Query("DELETE FROM cache_entries WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun pruneExpired(now: Long)

    /**
     * Deletes all entries of a specific cache type.
     * Used when the Cache System needs to fully invalidate one category
     * — for example, clearing all metadata cache after a provider change.
     */
    @Query("DELETE FROM cache_entries WHERE cacheType = :cacheType")
    suspend fun clearType(cacheType: String)

    /**
     * Deletes all cache entries across all types.
     * Used from Settings when the user manually clears all cache.
     */
    @Query("DELETE FROM cache_entries")
    suspend fun clearAll()
}