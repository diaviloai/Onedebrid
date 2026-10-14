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
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    // 1. Binds RealDebridProvider as the single DebridProvider (used by MediaRepositoryImpl)
    @Binds
    abstract fun bindPrimaryDebridProvider(
        realDebridProvider: RealDebridProvider
    ): DebridProvider

    // 2. Binds RealDebridProvider into the Set<DebridProvider> (used by ResolveStreamUseCase)
    @Binds
    @IntoSet
    abstract fun bindRealDebridProviderIntoSet(
        realDebridProvider: RealDebridProvider
    ): DebridProvider

    // Binds TMDB as the official MetadataProvider for the app
    @Binds
    abstract fun bindMetadataProvider(
        tmdbMetadataProvider: TmdbMetadataProvider
    ): MetadataProvider

    // Binds Torrentio as the official SearchProvider for the app
    @Binds
    abstract fun bindSearchProvider(
        torrentioSearchProvider: TorrentioSearchProvider
    ): SearchProvider
}