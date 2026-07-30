package com.onedebrid.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.onedebrid.app.data.local.dao.CacheEntryDao
import com.onedebrid.app.data.local.dao.ContinueWatchingDao
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.data.local.dao.ProfileDao
import com.onedebrid.app.data.local.dao.RecentlyPlayedDao
import com.onedebrid.app.data.local.dao.SearchHistoryDao
import com.onedebrid.app.data.local.entity.CacheEntryEntity
import com.onedebrid.app.data.local.entity.ContinueWatchingEntity
import com.onedebrid.app.data.local.entity.DownloadEntity
import com.onedebrid.app.data.local.entity.ProfileEntity
import com.onedebrid.app.data.local.entity.RecentlyPlayedEntity
import com.onedebrid.app.data.local.entity.SearchHistoryEntity
import com.onedebrid.app.data.local.Converters

@Database(
    entities = [
        ProfileEntity::class,
        ContinueWatchingEntity::class,
        SearchHistoryEntity::class,
        RecentlyPlayedEntity::class,
        DownloadEntity::class,
        CacheEntryEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun continueWatchingDao(): ContinueWatchingDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun recentlyPlayedDao(): RecentlyPlayedDao

    abstract fun downloadDao(): DownloadDao

    abstract fun cacheEntryDao(): CacheEntryDao
}