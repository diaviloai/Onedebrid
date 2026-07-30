package com.onedebrid.app.di

import android.content.Context
import androidx.room.Room
import com.onedebrid.app.data.local.AppDatabase
import com.onedebrid.app.data.local.dao.CacheEntryDao
import com.onedebrid.app.data.local.dao.ContinueWatchingDao
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.data.local.dao.ProfileDao
import com.onedebrid.app.data.local.dao.RecentlyPlayedDao
import com.onedebrid.app.data.local.dao.SearchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "onedebrid.db"
        ).build()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao =
        database.profileDao()

    @Provides
    fun provideContinueWatchingDao(database: AppDatabase): ContinueWatchingDao =
        database.continueWatchingDao()

    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao =
        database.searchHistoryDao()

    @Provides
    fun provideRecentlyPlayedDao(database: AppDatabase): RecentlyPlayedDao =
        database.recentlyPlayedDao()

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao =
        database.downloadDao()

    @Provides
    fun provideCacheEntryDao(database: AppDatabase): CacheEntryDao =
        database.cacheEntryDao()
}