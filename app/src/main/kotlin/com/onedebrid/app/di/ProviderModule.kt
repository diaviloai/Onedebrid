package com.onedebrid.app.di

import com.onedebrid.app.provider.debrid.DebridProvider
import com.onedebrid.app.provider.debrid.StubDebridProvider
import com.onedebrid.app.provider.metadata.MetadataProvider
import com.onedebrid.app.provider.metadata.StubMetadataProvider
import com.onedebrid.app.provider.search.SearchProvider
import com.onedebrid.app.provider.search.StubSearchProvider
import com.onedebrid.app.provider.subtitle.StubSubtitleProvider
import com.onedebrid.app.provider.subtitle.SubtitleProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @Singleton
    abstract fun bindDebridProvider(
        stub: StubDebridProvider
    ): DebridProvider

    @Binds
    @Singleton
    abstract fun bindMetadataProvider(
        stub: StubMetadataProvider
    ): MetadataProvider

    @Binds
    @Singleton
    abstract fun bindSearchProvider(
        stub: StubSearchProvider
    ): SearchProvider

    @Binds
    @Singleton
    abstract fun bindSubtitleProvider(
        stub: StubSubtitleProvider
    ): SubtitleProvider
}