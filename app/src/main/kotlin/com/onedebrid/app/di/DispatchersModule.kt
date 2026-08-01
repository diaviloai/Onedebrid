package com.onedebrid.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatchersModule {

    @Binds
    @Singleton
    abstract fun bindCoroutineDispatchers(
        impl: DefaultCoroutineDispatchers
    ): CoroutineDispatchers
}