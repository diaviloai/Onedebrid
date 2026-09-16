package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.usecase.RecordPlaybackUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.usecase.StartPlaybackUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testSupervisorJob = SupervisorJob()
    private val coordinatorScope = CoroutineScope(testDispatcher + testSupervisorJob)

    private val dispatchers = CoroutineDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
        unconfined = testDispatcher
    )

    private lateinit var fakeResolvePlaybackUseCase: FakeResolvePlaybackUseCase
    private lateinit var fakeStartPlaybackUseCase: FakeStartPlaybackUseCase
    private lateinit var fakeRecordPlaybackUseCase: FakeRecordPlaybackUseCase
    private lateinit var playbackCoordinator: PlaybackCoordinator

    @Before
    fun setup() {
        fakeResolvePlaybackUseCase = FakeResolvePlaybackUseCase()
        fakeStartPlaybackUseCase = FakeStartPlaybackUseCase()
        fakeRecordPlaybackUseCase = FakeRecordPlaybackUseCase()

        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = fakeResolvePlaybackUseCase,
            startPlaybackUseCase = fakeStartPlaybackUseCase,
            recordPlaybackUseCase = fakeRecordPlaybackUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )
    }

    @After
    fun tearDown() {
        testSupervisorJob.cancel()
    }

    @Test
    fun `play transitions to Ready on successful resolution and playback start`() = testScope.runTest {
        val request = PlaybackRequest(
            media = Media(id = "1", title = "Test Movie", type = MediaType.MOVIE)
        )
        val streamSource = StreamSource(
            id = "s1",
            mediaId = "1",
            url = "https://stream.url",
            quality = VideoQuality.HD_1080,
            fileSizeBytes = 1024L,
            fileName = "video.mkv",
            isCached = true
        )

        fakeResolvePlaybackUseCase.result = RepositoryResult.Success(streamSource)
        fakeStartPlaybackUseCase.result = RepositoryResult.Success(Unit)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue(state is PlaybackState.Ready)
        assertEquals(streamSource, (state as PlaybackState.Ready).source)
        assertEquals(1, fakeRecordPlaybackUseCase.recordedCalls.size)
    }

    @Test
    fun `play transitions to Error when resolvePlaybackUseCase fails`() = testScope.runTest {
        val request = PlaybackRequest(
            media = Media(id = "1", title = "Test Movie", type = MediaType.MOVIE)
        )
        val expectedError = AppError.StreamResolutionFailed

        fakeResolvePlaybackUseCase.result = RepositoryResult.Failure(expectedError)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue(state is PlaybackState.Error)
        assertEquals(expectedError, (state as PlaybackState.Error).error)
        assertEquals(0, fakeRecordPlaybackUseCase.recordedCalls.size)
    }

    @Test
    fun `stop resets state to Idle and cancels active jobs`() = testScope.runTest {
        playbackCoordinator.stop()

        assertEquals(PlaybackState.Idle, playbackCoordinator.state.value)
    }
}

private class FakeResolvePlaybackUseCase : ResolvePlaybackUseCase(
    mediaRepository = FakeMediaRepository()
) {
    var result: RepositoryResult<StreamSource> = RepositoryResult.Failure(AppError.Unknown)

    override suspend fun invoke(request: PlaybackRequest, profileId: String): RepositoryResult<StreamSource> {
        return result
    }
}

private class FakeStartPlaybackUseCase : StartPlaybackUseCase(
    sessionRepository = FakeSessionRepository(),
    dispatchers = CoroutineDispatchers(
        main = StandardTestDispatcher(),
        io = StandardTestDispatcher(),
        default = StandardTestDispatcher(),
        unconfined = StandardTestDispatcher()
    )
) {
    var result: RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun invoke(request: PlaybackRequest, source: StreamSource): RepositoryResult<Unit> {
        return result
    }
}

private class FakeRecordPlaybackUseCase : RecordPlaybackUseCase(
    playbackRepository = FakePlaybackRepository(),
    dispatchers = CoroutineDispatchers(
        main = StandardTestDispatcher(),
        io = StandardTestDispatcher(),
        default = StandardTestDispatcher(),
        unconfined = StandardTestDispatcher()
    )
) {
    val recordedCalls = mutableListOf<Triple<String, String, String?>>()

    override suspend fun invoke(profileId: String, mediaId: String, episodeId: String?): RepositoryResult<Unit> {
        recordedCalls.add(Triple(profileId, mediaId, episodeId))
        return RepositoryResult.Success(Unit)
    }
}
