package com.onedebrid.app.di

import com.onedebrid.app.provider.metadata.MetadataProvider
import com.onedebrid.app.provider.metadata.tmdb.TmdbMetadataProvider
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
}
