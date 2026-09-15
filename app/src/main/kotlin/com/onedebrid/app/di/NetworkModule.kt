package com.onedebrid.app.di

import com.onedebrid.app.provider.debrid.realdebrid.RealDebridApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ... existing provider methods (e.g., Retrofit, OkHttpClient)

    @Provides
    @Singleton
    fun provideRealDebridApi(retrofit: Retrofit): RealDebridApi {
        return retrofit.create(RealDebridApi::class.java)
    }
}
