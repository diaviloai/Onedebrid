package com.onedebrid.app.domain.usecase

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.error.ProviderError
import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.provider.debrid.AccountInfo
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveStreamUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val mockStream = StreamSource(
        id = "stream-1",
        mediaId = "media-123",
        url = "https://stream.debrid.com/video.mkv",
        quality = VideoQuality.HD_1080
    )

    @Test
    fun `when no providers configured returns AllProvidersUnavailable`() = runTest {
        val useCase = ResolveStreamUseCase(emptySet(), dispatchers)

        val result = useCase("test-hash")

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(AppError.AllProvidersUnavailable, (result as RepositoryResult.Failure).error)
    }

    @Test
    fun `when first provider succeeds returns stream source`() = runTest {
        val provider = FakeDebridProvider(
            id = "provider1",
            displayName = "Provider 1",
            result = ProviderResult.Success(mockStream)
        )
        val useCase = ResolveStreamUseCase(setOf(provider), dispatchers)

        val result = useCase("test-hash")

        assertTrue(result is RepositoryResult.Success)
        assertEquals(mockStream, (result as RepositoryResult.Success).data)
    }

    @Test
    fun `when first provider fails and second provider succeeds returns second provider stream`() = runTest {
        val provider1 = FakeDebridProvider(
            id = "provider1",
            displayName = "Provider 1",
            result = ProviderResult.Failure(ProviderError.NotFound)
        )
        val provider2 = FakeDebridProvider(
            id = "provider2",
            displayName = "Provider 2",
            result = ProviderResult.Success(mockStream)
        )
        val useCase = ResolveStreamUseCase(setOf(provider1, provider2), dispatchers)

        val result = useCase("test-hash")

        assertTrue(result is RepositoryResult.Success)
        assertEquals(mockStream, (result as RepositoryResult.Success).data)
    }

    @Test
    fun `when all providers fail returns failure`() = runTest {
        val provider1 = FakeDebridProvider(
            id = "provider1",
            displayName = "Provider 1",
            result = ProviderResult.Failure(ProviderError.NotFound)
        )
        val provider2 = FakeDebridProvider(
            id = "provider2",
            displayName = "Provider 2",
            result = ProviderResult.Failure(ProviderError.NotFound)
        )
        val useCase = ResolveStreamUseCase(setOf(provider1, provider2), dispatchers)

        val result = useCase("test-hash")

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(AppError.StreamResolutionFailed, (result as RepositoryResult.Failure).error)
    }

    private class FakeDebridProvider(
        override val id: String,
        override val displayName: String,
        private val result: ProviderResult<StreamSource>
    ) : DebridProvider {
        override suspend fun verifyAccount(): ProviderResult<AccountInfo> =
            ProviderResult.Success(AccountInfo("user", true))

        override suspend fun checkCache(hashes: List<String>): ProviderResult<Map<String, Boolean>> =
            ProviderResult.Success(hashes.associateWith { true })

        override suspend fun resolveStream(hash: String): ProviderResult<StreamSource> = result
    }
}
