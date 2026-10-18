package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.domain.usecase.StartPlaybackSessionUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var mockMediaRepository: MediaRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakePlaybackRepository: FakePlaybackRepository

    private lateinit var resolvePlaybackUseCase: ResolvePlaybackUseCase
    private lateinit var startPlaybackSessionUseCase: StartPlaybackSessionUseCase
    private lateinit var playbackCoordinator: PlaybackCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockMediaRepository = mockk()
        fakeSessionRepository = FakeSessionRepository()
        fakePlaybackRepository = FakePlaybackRepository()

        resolvePlaybackUseCase = ResolvePlaybackUseCase(mockMediaRepository)
        startPlaybackSessionUseCase = StartPlaybackSessionUseCase(fakeSessionRepository, fakePlaybackRepository, dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `play transitions to Ready on successful resolution and playback start`() = runTest(testDispatcher) {
        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackSessionUseCase = startPlaybackSessionUseCase,
            dispatchers = dispatchers,
            scope = this
        )

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

        val candidates = listOf(
            StreamCandidate(
                title = "Mock Stream",
                hash = "mock_hash_123",
                magnetUrl = "magnet:?xt=urn:btih:mock_hash_123",
                quality = VideoQuality.HD_1080
            )
        )

        coEvery { mockMediaRepository.searchStreamsByMedia(any(), any()) } returns RepositoryResult.Success(candidates)
        coEvery { mockMediaRepository.checkCacheStatus(any()) } returns RepositoryResult.Success(mapOf("mock_hash_123" to true))
        coEvery { mockMediaRepository.resolveStream(any()) } returns RepositoryResult.Success(streamSource)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue("Expected PlaybackState.Ready but was $state", state is PlaybackState.Ready)
        assertEquals(streamSource, (state as PlaybackState.Ready).stream)
        assertEquals(1, fakePlaybackRepository.recordedHistoryCalls.size)
    }

    @Test
    fun `play transitions to Error when resolvePlaybackUseCase fails`() = runTest(testDispatcher) {
        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackSessionUseCase = startPlaybackSessionUseCase,
            dispatchers = dispatchers,
            scope = this
        )

        val request = PlaybackRequest(
            media = Media(id = "1", title = "Test Movie", type = MediaType.MOVIE)
        )
        val expectedError = AppError.NoCachedStreamAvailable

        coEvery { mockMediaRepository.searchStreamsByMedia(any(), any()) } returns RepositoryResult.Failure(expectedError)

        playbackCoordinator.play(request, profileId = "profile_123")
        advanceUntilIdle()

        val state = playbackCoordinator.state.value
        assertTrue("Expected PlaybackState.Error but was $state", state is PlaybackState.Error)
        assertEquals(expectedError, (state as PlaybackState.Error).error)
        assertEquals(0, fakePlaybackRepository.recordedHistoryCalls.size)
    }

    @Test
    fun `stop resets state to Idle and cancels active jobs`() = runTest(testDispatcher) {
        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackSessionUseCase = startPlaybackSessionUseCase,
            dispatchers = dispatchers,
            scope = this
        )

        playbackCoordinator.stop()
        advanceUntilIdle()

        assertEquals(PlaybackState.Idle, playbackCoordinator.state.value)
    }
}

private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

private class FakeSessionRepository : SessionRepository {
    private var sessionState: SessionState? = SessionState(
        activeProfile = UserProfile(id = "profile_123", name = "Test User")
    )

    override fun initialise(profile: UserProfile) {
        sessionState = SessionState(activeProfile = profile)
    }

    override fun observeSession(): Flow<SessionState> = MutableSharedFlow()

    override fun getCurrentSession(): SessionState? = sessionState

    override suspend fun startPlaybackSession(request: PlaybackRequest, stream: StreamSource) {
        if (sessionState == null) {
            sessionState = SessionState(activeProfile = UserProfile(id = "profile_123", name = "Test User"))
        }
    }

    override suspend fun updatePlaybackPosition(positionMs: Long) {}

    override suspend fun endPlaybackSession() {}

    override suspend fun updateSearchSession(query: String, filters: Map<String, String>) {}

    override suspend fun clearSearchSession() {}

    override suspend fun clearSession() {
        sessionState = null
    }
}

private class FakePlaybackRepository : PlaybackRepository {
    val recordedHistoryCalls = mutableListOf<Triple<String, String, String?>>()

    override fun observeContinueWatching(profileId: String): Flow<List<WatchedItem>> = MutableSharedFlow()

    override suspend fun removeFromContinueWatching(profileId: String, mediaId: String) {}

    override suspend fun saveProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long
    ) {}

    override suspend fun getProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Long?> = RepositoryResult.Success(null)

    override suspend fun markAsCompleted(profileId: String, mediaId: String) {}

    override fun observeRecentlyPlayed(profileId: String): Flow<List<WatchedItem>> = MutableSharedFlow()

    override suspend fun recordPlayed(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {
        recordedHistoryCalls.add(Triple(profileId, mediaId, episodeId))
    }

    override suspend fun clearHistory(profileId: String) {}
}