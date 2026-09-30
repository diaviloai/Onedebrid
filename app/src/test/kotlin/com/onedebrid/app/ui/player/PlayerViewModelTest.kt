package com.onedebrid.app.ui/player

import androidx.lifecycle.SavedStateHandle
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.VideoQuality
import com.onedebrid.app.domain.usecase.SavePlaybackPositionUseCase
import com.onedebrid.app.usecase.EndPlaybackSessionUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetEpisodeByIdUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var fakePlaybackCoordinator: FakePlaybackCoordinator
    private lateinit var getMediaByIdUseCase: GetMediaByIdUseCase
    private lateinit var getEpisodeByIdUseCase: GetEpisodeByIdUseCase
    private lateinit var getActiveProfileUseCase: GetActiveProfileUseCase
    private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
    private lateinit var endPlaybackSessionUseCase: EndPlaybackSessionUseCase

    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakePlaybackRepository: FakePlaybackRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeMediaRepository = FakeMediaRepository()
        fakeSessionRepository = FakeSessionRepository()
        fakePlaybackRepository = FakePlaybackRepository()

        fakePlaybackCoordinator = FakePlaybackCoordinator()
        getMediaByIdUseCase = GetMediaByIdUseCase(fakeMediaRepository)
        getEpisodeByIdUseCase = GetEpisodeByIdUseCase(fakeMediaRepository)
        getActiveProfileUseCase = GetActiveProfileUseCase(fakeSessionRepository)
        savePlaybackPositionUseCase = SavePlaybackPositionUseCase(fakePlaybackRepository, dispatchers)
        endPlaybackSessionUseCase = EndPlaybackSessionUseCase(fakeSessionRepository, dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init resolves media and profile successfully`() = runTest(testDispatcher) {
        val media = Media(id = "m1", title = "Test Media", type = MediaType.MOVIE)
        fakeMediaRepository.mediaResult = RepositoryResult.Success(media)

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "mediaId" to "m1",
                "episodeId" to "none",
                "resumeMs" to -1L,
                "preferredSource" to ""
            )
        )

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = fakePlaybackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(ResolveState.Resolved, uiState.resolveState)
        assertEquals(1, fakePlaybackCoordinator.playCalls.size)
        assertEquals("m1", fakePlaybackCoordinator.playCalls.first().first.media.id)
    }

    @Test
    fun `init sets ResolveState Error on media resolution failure`() = runTest(testDispatcher) {
        val expectedError = AppError.NetworkError
        fakeMediaRepository.mediaResult = RepositoryResult.Failure(expectedError)

        val savedStateHandle = SavedStateHandle(
            mapOf(
                "mediaId" to "m1",
                "episodeId" to "none",
                "resumeMs" to -1L,
                "preferredSource" to ""
            )
        )

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = fakePlaybackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.resolveState is ResolveState.Error)
        assertEquals(expectedError, (uiState.resolveState as ResolveState.Error).error)
        assertEquals(0, fakePlaybackCoordinator.playCalls.size)
    }

    @Test
    fun `onPlayerStateChanged PLAYING starts position saving ticker`() = runTest(testDispatcher) {
        val media = Media(id = "m1", title = "Test Media", type = MediaType.MOVIE)
        fakeMediaRepository.mediaResult = RepositoryResult.Success(media)

        val savedStateHandle = SavedStateHandle(mapOf("mediaId" to "m1"))

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = fakePlaybackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        viewModel.onPlayerStateChanged(PlayerLifecycleState.PLAYING, positionMs = 10_000L, durationMs = 100_000L)

        // Advance 5 seconds to trigger one tick
        advanceTimeBy(5_000L)
        advanceUntilIdle()

        assertEquals(1, fakePlaybackRepository.savedProgressCalls.size)
        assertEquals(10_000L, fakePlaybackRepository.savedProgressCalls.first().positionMs)

        // Advance another 5 seconds to trigger a second tick
        advanceTimeBy(5_000L)
        advanceUntilIdle()

        assertEquals(2, fakePlaybackRepository.savedProgressCalls.size)
    }

    @Test
    fun `onPlayerStateChanged PAUSED stops ticker and saves position immediately`() = runTest(testDispatcher) {
        val media = Media(id = "m1", title = "Test Media", type = MediaType.MOVIE)
        fakeMediaRepository.mediaResult = RepositoryResult.Success(media)

        val savedStateHandle = SavedStateHandle(mapOf("mediaId" to "m1"))

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = fakePlaybackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        viewModel.onPlayerStateChanged(PlayerLifecycleState.PAUSED, positionMs = 25_000L, durationMs = 100_000L)
        advanceUntilIdle()

        assertEquals(1, fakePlaybackRepository.savedProgressCalls.size)
        assertEquals(25_000L, fakePlaybackRepository.savedProgressCalls.first().positionMs)
    }

    @Test
    fun `stop stops playback coordinator and ends playback session`() = runTest(testDispatcher) {
        val media = Media(id = "m1", title = "Test Media", type = MediaType.MOVIE)
        fakeMediaRepository.mediaResult = RepositoryResult.Success(media)

        val savedStateHandle = SavedStateHandle(mapOf("mediaId" to "m1"))

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = fakePlaybackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        viewModel.stop()
        advanceUntilIdle()

        assertTrue(fakePlaybackCoordinator.stopCalled)
        assertTrue(fakeSessionRepository.endedPlaybackSession)
    }
}

private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

private class FakePlaybackCoordinator : PlaybackCoordinator(
    resolvePlaybackUseCase = org.mockito.kotlin.mock(),
    startPlaybackSessionUseCase = org.mockito.kotlin.mock(),
    recordPlaybackUseCase = org.mockito.kotlin.mock(),
    dispatchers = TestCoroutineDispatchers(StandardTestDispatcher()),
    scope = kotlinx.coroutines.MainScope()
) {
    val playCalls = mutableListOf<Pair<com.onedebrid.app.domain.model.PlaybackRequest, String>>()
    var stopCalled = false
    private val _coordinatorState = MutableStateFlow<CoordinatorState>(CoordinatorState.Idle)

    override val state = _coordinatorState

    override fun play(request: com.onedebrid.app.domain.model.PlaybackRequest, profileId: String) {
        playCalls.add(request to profileId)
        _coordinatorState.value = CoordinatorState.Ready(
            StreamSource("s1", request.media.id, "https://stream.url", VideoQuality.HD_1080, 100L, "file.mp4", true)
        )
    }

    override fun stop() {
        stopCalled = true
        _coordinatorState.value = CoordinatorState.Idle
    }
}

private class FakeMediaRepository : com.onedebrid.app.data.repository.MediaRepository {
    var mediaResult: RepositoryResult<Media> = RepositoryResult.Failure(AppError.Unknown("Not set"))
    var episodeResult: RepositoryResult<Episode> = RepositoryResult.Failure(AppError.Unknown("Not set"))

    override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> = mediaResult

    override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> =
        RepositoryResult.Failure(AppError.Unknown("Not implemented"))

    override suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode> = episodeResult

    override suspend fun resolveStream(candidate: com.onedebrid.app.domain.model.StreamCandidate): RepositoryResult<StreamSource> =
        RepositoryResult.Failure(AppError.Unknown("Not implemented"))

    override suspend fun checkCacheStatus(candidates: List<com.onedebrid.app.domain.model.StreamCandidate>): RepositoryResult<Map<String, Boolean>> =
        RepositoryResult.Success(emptyMap())

    override suspend fun search(query: String, profileId: String): RepositoryResult<List<com.onedebrid.app.domain.model.SearchResult>> =
        RepositoryResult.Failure(AppError.Unknown("Not implemented"))

    override suspend fun searchStreamsByMedia(
        media: Media,
        episode: Episode?
    ): RepositoryResult<List<com.onedebrid.app.domain.model.StreamCandidate>> =
        RepositoryResult.Failure(AppError.Unknown("Not implemented"))
}

private class FakeSessionRepository : com.onedebrid.app.data.repository.SessionRepository {
    var endedPlaybackSession = false

    override fun initialise(profile: UserProfile) {}

    override fun observeSession(): Flow<com.onedebrid.app.domain.model.SessionState> =
        flowOf(com.onedebrid.app.domain.model.SessionState(activeProfile = UserProfile("profile_123", "Test User")))

    override fun getCurrentSession(): com.onedebrid.app.domain.model.SessionState =
        com.onedebrid.app.domain.model.SessionState(activeProfile = UserProfile("profile_123", "Test User"))

    override suspend fun startPlaybackSession(
        request: com.onedebrid.app.domain.model.PlaybackRequest,
        stream: StreamSource
    ) {}

    override suspend fun updatePlaybackPosition(positionMs: Long) {}

    override suspend fun endPlaybackSession() {
        endedPlaybackSession = true
    }

    override suspend fun updateSearchSession(query: String, filters: Map<String, String>) {}

    override suspend fun clearSearchSession() {}

    override suspend fun clearSession() {}
}

private class FakePlaybackRepository : com.onedebrid.app.data.repository.PlaybackRepository {
    data class ProgressCall(
        val profileId: String,
        val mediaId: String,
        val episodeId: String?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val positionMs: Long,
        val durationMs: Long
    )

    val savedProgressCalls = mutableListOf<ProgressCall>()

    override fun observeContinueWatching(profileId: String): Flow<List<com.onedebrid.app.domain.model.WatchedItem>> = flowOf(emptyList())

    override suspend fun removeFromContinueWatching(profileId: String, mediaId: String) {}

    override suspend fun saveProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionMs: Long,
        durationMs: Long
    ) {
        savedProgressCalls.add(
            ProgressCall(profileId, mediaId, episodeId, seasonNumber, episodeNumber, positionMs, durationMs)
        )
    }

    override suspend fun getProgress(
        profileId: String,
        mediaId: String,
        episodeId: String?
    ): RepositoryResult<Long?> = RepositoryResult.Success(null)

    override suspend fun markAsCompleted(profileId: String, mediaId: String) {}

    override fun observeRecentlyPlayed(profileId: String): Flow<List<com.onedebrid.app.domain.model.WatchedItem>> = flowOf(emptyList())

    override suspend fun recordPlayed(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {}

    override suspend fun clearHistory(profileId: String) {}
}
