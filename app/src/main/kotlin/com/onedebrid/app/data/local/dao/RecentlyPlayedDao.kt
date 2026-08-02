package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {

    @Query("""
        SELECT * FROM recently_played
        WHERE profileId = :profileId
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
    """)
    fun observeRecentlyPlayed(
        profileId: String,
        limit: Int = 20
    ): Flow<List<RecentlyPlayedEntity>>

    @Query("""
        SELECT * FROM recently_played
        WHERE profileId = :profileId AND mediaId = :mediaId
        LIMIT 1
    """)
    suspend fun getEntryForMedia(
        profileId: String,
        mediaId: String
    ): RecentlyPlayedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: RecentlyPlayedEntity)

    @Query("""
        DELETE FROM recently_played
        WHERE profileId = :profileId AND mediaId = :mediaId
    """)
    suspend fun removeEntry(profileId: String, mediaId: String)

    @Query("DELETE FROM recently_played WHERE profileId = :profileId")
    suspend fun clearAllForProfile(profileId: String)
}