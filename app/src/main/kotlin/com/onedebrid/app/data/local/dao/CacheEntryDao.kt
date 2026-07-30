package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.CacheEntryEntity

@Dao
interface CacheEntryDao {

    // --- Reads ---

    @Query("""
        SELECT * FROM cache_entries
        WHERE cacheType = :cacheType AND key = :cacheKey
        AND (expiresAt IS NULL OR expiresAt > :now)
        LIMIT 1
    """)
    suspend fun getEntry(
        cacheType: String,
        cacheKey: String,
        now: Long
    ): CacheEntryEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: CacheEntryEntity)

    // --- Deletion ---

    @Query("""
        DELETE FROM cache_entries
        WHERE cacheType = :cacheType AND key = :cacheKey
    """)
    suspend fun deleteEntry(cacheType: String, cacheKey: String)

    @Query("DELETE FROM cache_entries WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun pruneExpired(now: Long)

    @Query("DELETE FROM cache_entries WHERE cacheType = :cacheType")
    suspend fun clearType(cacheType: String)

    @Query("DELETE FROM cache_entries")
    suspend fun clearAll()
}