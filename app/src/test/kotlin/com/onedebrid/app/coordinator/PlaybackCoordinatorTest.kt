package com.onedebrid.app.coordinator

import com.google.common.truth.Truth.assertThat
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.provider.debrid.AccountInfo
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockDebridProvider: FakeDebridProvider

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockDebridProvider = FakeDebridProvider()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fakeDebridProvider_resolvesStreamSuccessfully() = runTest {
        val testHash = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        val expectedUrl = "https://download.real-debrid.com/cdn/movie.mkv"

        mockDebridProvider.nextResult = ProviderResult.Success(
            StreamSource(
                id = "stream_1",
                mediaId = "550",
                url = expectedUrl,
                quality = VideoQuality.UNKNOWN,
                isCached = true
            )
        )

        val result = mockDebridProvider.resolveStream(testHash)

        assertThat(result).isInstanceOf(ProviderResult.Success::class.java)
        val stream = (result as ProviderResult.Success).data
        assertThat(stream.url).isEqualTo(expectedUrl)
        assertThat(stream.isCached).isTrue()
    }

    @Test
    fun fakeDebridProvider_handlesFailureCorrectly() = runTest {
        mockDebridProvider.nextResult = ProviderResult.Failure(
            ProviderError.NotFound
        )

        val result = mockDebridProvider.resolveStream("invalid_hash")

        assertThat(result).isInstanceOf(ProviderResult.Failure::class.java)
        val error = (result as ProviderResult.Failure).error
        assertThat(error).isEqualTo(ProviderError.NotFound)
    }
}

/**
 * Fake DebridProvider implementation fully satisfying the contract.
 */
private class FakeDebridProvider : DebridProvider {
    override val id: String = "fake_debrid"
    override val displayName: String = "Fake Debrid"

    var nextResult: ProviderResult<StreamSource> = ProviderResult.Failure(
        ProviderError.NotFound
    )

    override suspend fun resolveStream(hash: String): ProviderResult<StreamSource> {
        return nextResult
    }

    override suspend fun verifyAccount(): ProviderResult<AccountInfo> {
        return ProviderResult.Failure(ProviderError.AuthenticationFailed)
    }

    override suspend fun checkCache(hashes: List<String>): ProviderResult<Map<String, Boolean>> {
        return ProviderResult.Success(hashes.associateWith { true })
    }
}
