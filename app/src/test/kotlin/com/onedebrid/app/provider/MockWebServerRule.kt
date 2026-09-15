package com.onedebrid.app.provider

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

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

    fun enqueueResponse(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    fun <T> createApi(apiClass: Class<T>): T {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(apiClass)
    }

    fun takeRequest(timeoutSeconds: Long = 2): RecordedRequest? {
        return server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
    }
}
