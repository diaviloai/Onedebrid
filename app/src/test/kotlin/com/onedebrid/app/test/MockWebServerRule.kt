package com.onedebrid.app.test

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * A JUnit Rule that manages a single MockWebServer instance for network integration testing.
 * Provides helper factory methods to build Retrofit API instances pointing directly to the mock server.
 */
class MockWebServerRule : ExternalResource() {

    val server: MockWebServer = MockWebServer()

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .build()

    override fun before() {
        server.start()
    }

    override fun after() {
        server.shutdown()
    }

    /**
     * Enqueues a response with HTTP code 200 and a JSON string body.
     */
    fun enqueueResponse(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    /**
     * Creates an instance of a Retrofit API interface bound to the MockWebServer root URL.
     */
    fun <T> createApi(apiClass: Class<T>): T {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(apiClass)
    }

    /**
     * Convenience function to take and inspect the next recorded request from the queue.
     */
    fun takeRequest(timeoutSeconds: Long = 2): RecordedRequest? {
        return server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
    }
}
