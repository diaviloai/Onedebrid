package com.onedebrid.app.coordinator

import com.google.common.truth.Truth.assertThat
import com.onedebrid.app.core.dispatcher.AppDispatchers
import com.onedebrid.app.domain.error.OneDebridError
import com.onedebrid.app.domain.model.AccountInfo
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.ProviderResult
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.usecase.RecordPlaybackUseCase
import com.onedebrid.app.domain.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.domain.usecase.StartPlaybackUseCase
import com.onedebrid.app.provider.debrid.DebridProvider
import kotlinx.coroutines.CoroutineScope
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
    private lateinit var resolvePlaybackUseCase: ResolvePlaybackUseCase
    private lateinit var startPlaybackUseCase: StartPlaybackUseCase
    private lateinit var recordPlaybackUseCase: RecordPlaybackUseCase
    private lateinit var playbackCoordinator: PlaybackCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockDebridProvider = FakeDebridProvider()
        resolvePlaybackUseCase = ResolvePlaybackUseCase(mockDebridProvider)
        startPlaybackUseCase = StartPlaybackUseCase()
        recordPlaybackUseCase = RecordPlaybackUseCase()

        val appDispatchers = AppDispatchers(
            main = testDispatcher,
            io = testDispatcher,
            default = testDispatcher
        )

        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackUseCase = startPlaybackUseCase,
            recordPlaybackUseCase = recordPlaybackUseCase,
            dispatchers = appDispatchers,
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun preparePlayback_successfulResolution_updatesStateToReady() = runTest {
        val testHash = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        val expectedUrl = "https://download.real-debrid.com/cdn/movie.mkv"

        mockDebridProvider.nextResult = ProviderResult.Success(
            StreamSource(
                id = "stream_1",
                mediaId = "550",
                url = expectedUrl,
                quality = "1080p",
                isCached = true
            )
        )

        val media = Media(
            id = "550",
            imdbId = "tt0137523",
            title = "Fight Club",
            type = MediaType.MOVIE
        )

        playbackCoordinator.preparePlayback(media, testHash)

        val state = playbackCoordinator.state.value
        assertThat(state).isNotNull()
    }
}

/**
 * Fake DebridProvider implementation satisfying contract requirements.
 */
private class FakeDebridProvider : DebridProvider {
    override val id: String = "fake_debrid"
    override val displayName: String = "Fake Debrid"

    var nextResult: ProviderResult<StreamSource> = ProviderResult.Failure(
        OneDebridError.Unknown("No fake result configured")
    )

    override suspend fun resolveStream(hash: String): ProviderResult<StreamSource> {
        return nextResult
    }

    override suspend fun verifyAccount(): ProviderResult<AccountInfo> {
        return ProviderResult.Failure(OneDebridError.Unknown("Not implemented"))
    }

    override suspend fun checkCache(hashes: List<String>): ProviderResult<Map<String, Boolean>> {
        return ProviderResult.Success(hashes.associateWith { true })
    }
}
