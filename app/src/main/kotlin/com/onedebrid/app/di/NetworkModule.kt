package com.onedebrid.app.di

import com.onedebrid.app.provider.debrid.realdebrid.RealDebridApi
import com.onedebrid.app.provider.metadata.tmdb.TmdbApi
import com.onedebrid.app.provider.search.torrentio.TorrentioApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealDebridHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    @DefaultHttpClient
    fun provideDefaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    @TmdbHttpClient
    fun provideTmdbOkHttpClient(@DefaultHttpClient baseClient: OkHttpClient): OkHttpClient {
        val tmdbInterceptor = Interceptor { chain ->
            // TODO (Phase 2): Replace with actual TMDB API Key from BuildConfig or Settings
            val tmdbApiKey = "YOUR_TMDB_API_KEY" 
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $tmdbApiKey")
                .build()
            chain.proceed(request)
        }
        return baseClient.newBuilder()
            .addInterceptor(tmdbInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @RealDebridHttpClient
    fun provideRealDebridOkHttpClient(@DefaultHttpClient baseClient: OkHttpClient): OkHttpClient {
        val rdInterceptor = Interceptor { chain ->
            // TODO (Phase 2): Replace with actual RD API Key from DataStore/SettingsRepository
            val rdApiKey = "YOUR_RD_API_KEY"
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $rdApiKey")
                .build()
            chain.proceed(request)
        }
        return baseClient.newBuilder()
            .addInterceptor(rdInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(
        @TmdbHttpClient okHttpClient: OkHttpClient,
        json: Json
    ): TmdbApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(TmdbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTorrentioApi(
        @DefaultHttpClient okHttpClient: OkHttpClient,
        json: Json
    ): TorrentioApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://torrentio.strem.fun/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(TorrentioApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRealDebridApi(
        @RealDebridHttpClient okHttpClient: OkHttpClient,
        json: Json
    ): RealDebridApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.real-debrid.com/rest/1.0/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(RealDebridApi::class.java)
    }
}