package com.onedebrid.app.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import com.onedebrid.app.data.local.dao.CacheEntryDao
import com.onedebrid.app.data.local.dao.ContinueWatchingDao
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.data.local.dao.ProfileDao
import com.onedebrid.app.data.local.dao.RecentlyPlayedDao
import com.onedebrid.app.data.local.dao.SearchHistoryDao

@Entity(tableName = "placeholder_entities")
data class PlaceholderEntity(
    @PrimaryKey val id: Int = 1
)

@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun downloadDao(): DownloadDao
    abstract fun cacheEntryDao(): CacheEntryDao
}
