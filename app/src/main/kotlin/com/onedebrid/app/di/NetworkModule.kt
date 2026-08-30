package com.onedebrid.app.di

import com.onedebrid.app.provider.search.torrentio.TorrentioApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Provides networking infrastructure (OkHttp, Retrofit, per-provider API
 * interfaces) for provider implementations that make real HTTP calls.
 *
 * Session 29: first real Retrofit/OkHttp wiring in the app — Retrofit,
 * OkHttp, and the kotlinx.serialization converter were already declared
 * as dependencies (build.gradle.kts / libs.versions.toml) but unused
 * until TorrentioSearchProvider needed them.
 *
 * Each external base URL gets its own qualified Retrofit instance rather
 * than one shared instance, since different providers (Torrentio today;
 * TMDB, Real-Debrid, OpenSubtitles later) have different base URLs and
 * potentially different timeout/header needs. A shared OkHttpClient is
 * still reused as the base for all of them, per Technical Standards v0.1
 * (avoid unnecessary duplication).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TORRENTIO_BASE_URL = "https://torrentio.strem.fun/"

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class TorrentioRetrofit

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC rather than BODY — avoids dumping full response bodies
            // (which can be large for a multi-provider stream list) into
            // logcat on every search. Level can be raised locally when
            // debugging a specific parsing issue.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @TorrentioRetrofit
    fun provideTorrentioRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(TORRENTIO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTorrentioApi(
        @TorrentioRetrofit retrofit: Retrofit
    ): TorrentioApi = retrofit.create(TorrentioApi::class.java)
}

private fun String.toMediaType() = okhttp3.MediaType.Companion.get(this)