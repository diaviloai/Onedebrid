package com.onedebrid.app.di

import com.onedebrid.app.BuildConfig
import com.onedebrid.app.provider.metadata.tmdb.TmdbApi
import com.onedebrid.app.provider.search.torrentio.TorrentioApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
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
 * Session 29: first real Retrofit/OkHttp wiring in the app (Torrentio).
 *
 * TMDB added in the real-MetadataProvider session — TMDB requires
 * Bearer-token auth on every request (unlike Torrentio, which is
 * keyless), so it gets its own OkHttpClient with an AuthInterceptor
 * attached, rather than sharing Torrentio's client. This is deliberate:
 * an auth header meant for TMDB must never be sent to Torrentio (or any
 * other future keyless/differently-authed provider), and vice versa.
 *
 * Each external base URL gets its own qualified Retrofit instance, per
 * the reasoning already established for Torrentio below.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TORRENTIO_BASE_URL = "https://torrentio.strem.fun/"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class TorrentioRetrofit

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class TmdbRetrofit

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class TmdbOkHttpClient

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // BASIC rather than BODY — avoids dumping full response bodies
            // into logcat on every request. Level can be raised locally
            // when debugging a specific parsing issue.
            level = HttpLoggingInterceptor.Level.BASIC
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        logging: HttpLoggingInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

    /**
     * Adds "Authorization: Bearer <token>" to every request. Token comes
     * from BuildConfig, which is populated at build time from
     * local.properties (local dev) or a GitHub Actions secret (CI) — see
     * app/build.gradle.kts. Never hardcoded, never committed.
     */
    private class AuthInterceptor(private val token: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            return chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    @TmdbOkHttpClient
    fun provideTmdbOkHttpClient(
        logging: HttpLoggingInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(BuildConfig.TMDB_READ_ACCESS_TOKEN))
            .addInterceptor(logging)
            .build()

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

    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(
        @TmdbOkHttpClient okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(
        @TmdbRetrofit retrofit: Retrofit
    ): TmdbApi = retrofit.create(TmdbApi::class.java)
}