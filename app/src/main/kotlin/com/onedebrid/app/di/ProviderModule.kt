package com.onedebrid.app.di

import com.onedebrid.app.provider.debrid.DebridProvider
import com.onedebrid.app.provider.debrid.realdebrid.RealDebridProvider
import com.onedebrid.app.provider.metadata.MetadataProvider
import com.onedebrid.app.provider.metadata.tmdb.TmdbMetadataProvider
import com.onedebrid.app.provider.search.SearchProvider
import com.onedebrid.app.provider.search.torrentio.TorrentioSearchProvider
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
    abstract fun bindMetadataProvider(
        impl: TmdbMetadataProvider
    ): MetadataProvider

    @Binds
    @Singleton
    abstract fun bindSearchProvider(
        impl: TorrentioSearchProvider
    ): SearchProvider

    @Binds
    @Singleton
    abstract fun bindDebridProvider(
        impl: RealDebridProvider
    ): DebridProvider
}
