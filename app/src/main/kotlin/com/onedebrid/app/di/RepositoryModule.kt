package com.onedebrid.app.di

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.MediaRepositoryImpl
import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.PlaybackRepositoryImpl
import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.ProfileRepositoryImpl
import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.data.repository.SearchRepositoryImpl
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.data.repository.SessionRepositoryImpl
import com.onedebrid.app.data.repository.SubtitleRepository
import com.onedebrid.app.data.repository.SubtitleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        impl: PlaybackRepositoryImpl
    ): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleRepository(
        impl: SubtitleRepositoryImpl
    ): SubtitleRepository
}