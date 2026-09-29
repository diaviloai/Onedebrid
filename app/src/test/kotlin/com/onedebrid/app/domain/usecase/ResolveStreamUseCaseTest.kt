package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.provider.debrid.DebridStreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveStreamUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )

    private val sampleMedia = Media(
        id = "movie-1",
        title = "Test Movie",
        type = MediaType.MOVIE,
        year = 2024
    )
    private val request = PlaybackRequest(media = sampleMedia)

    private val mockStream = StreamSource(
        url = "https://stream.debrid.com/video.mkv",
        quality = "1080p",
        providerName = "Real-Debrid"
    )

    @Test
    fun `when no providers enabled returns AllProvidersUnavailable`() = runTest {
        val disabledProvider = FakeDebridProvider(enabled = false)
        val useCase = ResolveStreamUseCase(listOf(disabledProvider), dispatchers)

        val result = useCase(request)

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(AppError.AllProvidersUnavailable, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun `when first provider succeeds returns stream source`() = runTest {
        val provider = FakeDebridProvider(
            enabled = true,
            result = RepositoryResult.Success(mockStream)
        )
        val useCase = ResolveStreamUseCase(listOf(provider), dispatchers)

        val result = useCase(request)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(mockStream, (result as RepositoryResult.Success).data)
    }

    @Test
    fun `when first provider fails and second provider succeeds returns second provider stream`() = runTest {
        val provider1 = FakeDebridProvider(
            enabled = true,
            result = RepositoryResult.Failure(AppError.NoCachedStreamAvailable)
        )
        val provider2 = FakeDebridProvider(
            enabled = true,
            result = RepositoryResult.Success(mockStream)
        )
        val useCase = ResolveStreamUseCase(listOf(provider1, provider2), dispatchers)

        val result = useCase(request)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(mockStream, (result as RepositoryResult.Success).data)
    }

    @Test
    fun `when all providers fail returns failure`() = runTest {
        val provider1 = FakeDebridProvider(
            enabled = true,
            result = RepositoryResult.Failure(AppError.NoCachedStreamAvailable)
        )
        val provider2 = FakeDebridProvider(
            enabled = true,
            result = RepositoryResult.Failure(AppError.NoCachedStreamAvailable)
        )
        val useCase = ResolveStreamUseCase(listOf(provider1, provider2), dispatchers)

        val result = useCase(request)

        assertTrue(result is RepositoryResult.Failure)
    }

    private class FakeDebridProvider(
        private val enabled: Boolean,
        private val result: RepositoryResult<StreamSource> = RepositoryResult.Failure(AppError.AllProvidersUnavailable)
    ) : DebridStreamProvider {
        override fun isEnabled(): Boolean = enabled
        override suspend fun resolveStream(request: PlaybackRequest): RepositoryResult<StreamSource> = result
    }
}
