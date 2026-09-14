package com.onedebrid.app.di

import com.onedebrid.app.provider.debrid.DebridProvider
import com.onedebrid.app.provider.debrid.StubDebridProvider // Or real provider implementation
import com.onedebrid.app.provider.metadata.MetadataProvider
import com.onedebrid.app.provider.metadata.tmdb.TmdbMetadataProvider
import com.onedebrid.app.provider.search.SearchProvider
import com.onedebrid.app.provider.search.StubSearchProvider // Or real provider implementation
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
        impl: StubSearchProvider // Replace with concrete Torrentio/Search implementation when ready
    ): SearchProvider

    @Binds
    @Singleton
    abstract fun bindDebridProvider(
        impl: StubDebridProvider // Replace with concrete RealDebrid/Debrid implementation when ready
    ): DebridProvider
}
