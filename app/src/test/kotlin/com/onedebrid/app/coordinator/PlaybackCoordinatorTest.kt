package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as SessionPlaybackState
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.usecase.RecordPlaybackUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.usecase.StartPlaybackUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakePlaybackRepository: FakePlaybackRepository

    private lateinit var resolvePlaybackUseCase: ResolvePlaybackUseCase
    private lateinit var startPlaybackUseCase: StartPlaybackUseCase
    private lateinit var recordPlaybackUseCase: RecordPlaybackUseCase
    private lateinit var playbackCoordinator: PlaybackCoordinator

    @Before
    fun setup() {
        fakeMediaRepository = FakeMediaRepository()
        fakeSessionRepository = FakeSessionRepository()
        fakePlaybackRepository = FakePlaybackRepository()

        resolvePlaybackUseCase = ResolvePlaybackUseCase(fakeMediaRepository)
        startPlaybackUseCase = StartPlaybackUseCase(fakeSessionRepository, dispatchers)
        recordPlaybackUseCase = RecordPlaybackUseCase(fakePlaybackRepository, dispatchers)

        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackUseCase = startPlaybackUseCase,
            recordPlaybackUseCase = recordPlaybackUseCase,
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

        fakeMediaRepository.resolveStreamResult = RepositoryResult.Success(streamSource)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue(state is PlaybackState.Ready)
        assertEquals(streamSource, (state as PlaybackState.Ready).source)
        assertEquals(1, fakePlaybackRepository.recordedHistoryCalls.size)
    }

    @Test
    fun `play transitions to Error when resolvePlaybackUseCase fails`() = testScope.runTest {
        val request = PlaybackRequest(
            media = Media(id = "1", title = "Test Movie", type = MediaType.MOVIE)
        )
        val expectedError = AppError.StreamResolutionFailed

        fakeMediaRepository.resolveStreamResult = RepositoryResult.Failure(expectedError)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue(state is PlaybackState.Error)
        assertEquals(expectedError, (state as PlaybackState.Error).error)
        assertEquals(0, fakePlaybackRepository.recordedHistoryCalls.size)
    }

    @Test
    fun `stop resets state to Idle and cancels active jobs`() = testScope.runTest {
        playbackCoordinator.stop()

        assertEquals(PlaybackState.Idle, playbackCoordinator.state.value)
    }
}

private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
}

private class FakeMediaRepository : MediaRepository {
    var resolveStreamResult: RepositoryResult<StreamSource> = RepositoryResult.Failure(AppError.Unknown)

    override suspend fun getMediaDetails(mediaId: String, type: MediaType): RepositoryResult<Media> =
        RepositoryResult.Failure(AppError.Unknown)

    override suspend fun getEpisodes(mediaId: String, seasonNumber: Int): RepositoryResult<List<Episode>> =
        RepositoryResult.Failure(AppError.Unknown)

    override suspend fun getEpisodeById(episodeId: String): RepositoryResult<Episode> =
        RepositoryResult.Failure(AppError.Unknown)

    override suspend fun resolveStream(request: PlaybackRequest): RepositoryResult<StreamSource> =
        resolveStreamResult

    override suspend fun checkCacheStatus(infoHashes: List<String>): RepositoryResult<Map<String, Boolean>> =
        RepositoryResult.Failure(AppError.Unknown)

    override suspend fun search(query: String): RepositoryResult<List<Media>> =
        RepositoryResult.Failure(AppError.Unknown)

    override suspend fun searchStreamsByMedia(mediaId: String, episodeId: String?): RepositoryResult<List<StreamCandidate>> =
        RepositoryResult.Failure(AppError.Unknown)
}

private class FakeSessionRepository : SessionRepository {
    override fun initialise(profile: UserProfile) {}
    override fun observeSession(): Flow<SessionState> = MutableSharedFlow()
    override fun getCurrentSession(): SessionState? = null
    override suspend fun startPlaybackSession(request: PlaybackRequest, stream: StreamSource) {}
    override suspend fun updatePlaybackPosition(positionMs: Long) {}
    override suspend fun endPlaybackSession() {}
    override suspend fun updateSearchSession(query: String, filters: Map<String, String>) {}
    override suspend fun clearSearchSession() {}
    override suspend fun clearSession() {}
}

private class FakePlaybackRepository : PlaybackRepository {
    val recordedHistoryCalls = mutableListOf<Triple<String, String, String?>>()

    override suspend fun recordPlaybackHistory(
        profileId: String,
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Unit> {
        recordedHistoryCalls.add(Triple(profileId, mediaId, episodeId))
        return RepositoryResult.Success(Unit)
    }

    override suspend fun updatePlaybackPosition(
        profileId: String,
        mediaId: String,
        positionMs: Long,
        durationMs: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun getPlaybackState(
        profileId: String,
        mediaId: String
    ): RepositoryResult<SessionPlaybackState> = RepositoryResult.Failure(AppError.Unknown)
}
