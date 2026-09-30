package com.onedebrid.app.ui.player

import androidx.lifecycle.SavedStateHandle
import com.onedebrid.app.coordinator.PlaybackCoordinator
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.domain.usecase.SavePlaybackPositionUseCase
import com.onedebrid.app.usecase.EndPlaybackSessionUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetEpisodeByIdUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.RecordPlaybackUseCase
import com.onedebrid.app.usecase.ResolvePlaybackUseCase
import com.onedebrid.app.usecase.StartPlaybackSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private lateinit var playbackCoordinator: PlaybackCoordinator
    private lateinit var resolvePlaybackUseCase: ResolvePlaybackUseCase
    private lateinit var startPlaybackSessionUseCase: StartPlaybackSessionUseCase
    private lateinit var recordPlaybackUseCase: RecordPlaybackUseCase

    private lateinit var getMediaByIdUseCase: GetMediaByIdUseCase
    private lateinit var getEpisodeByIdUseCase: GetEpisodeByIdUseCase
    private lateinit var getActiveProfileUseCase: GetActiveProfileUseCase
    private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
    private lateinit var endPlaybackSessionUseCase: EndPlaybackSessionUseCase

    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var fakeProfileRepository: FakeProfileRepository
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakePlaybackRepository: FakePlaybackRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeMediaRepository = FakeMediaRepository()
        fakeProfileRepository = FakeProfileRepository()
        fakeSessionRepository = FakeSessionRepository()
        fakePlaybackRepository = FakePlaybackRepository()

        resolvePlaybackUseCase = ResolvePlaybackUseCase(fakeMediaRepository)
        startPlaybackSessionUseCase = StartPlaybackSessionUseCase(fakeSessionRepository)
        recordPlaybackUseCase = RecordPlaybackUseCase(fakePlaybackRepository)

        playbackCoordinator = PlaybackCoordinator(
            resolvePlaybackUseCase = resolvePlaybackUseCase,
            startPlaybackSessionUseCase = startPlaybackSessionUseCase,
            recordPlaybackUseCase = recordPlaybackUseCase,
            dispatchers = dispatchers,
            scope = TestScope(testDispatcher)
        )

        getMediaByIdUseCase = GetMediaByIdUseCase(fakeMediaRepository, dispatchers)
        getEpisodeByIdUseCase = GetEpisodeByIdUseCase(fakeMediaRepository, dispatchers)
        getActiveProfileUseCase = GetActiveProfileUseCase(fakeProfileRepository)
        savePlaybackPositionUseCase = SavePlaybackPositionUseCase(
            playbackRepository = fakePlaybackRepository,
            sessionRepository = fakeSessionRepository,
            dispatchers = dispatchers
        )
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
            playbackCoordinator = playbackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(ResolveState.Resolved, uiState.resolveState)
    }

    @Test
    fun `init sets ResolveState Error on media resolution failure`() = runTest(testDispatcher) {
        val expectedError = AppError.Unknown("Media not found")
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
            playbackCoordinator = playbackCoordinator,
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
    }

    @Test
    fun `onPlayerStateChanged PLAYING starts position saving ticker`() = runTest(testDispatcher) {
        val media = Media(id = "m1", title = "Test Media", type = MediaType.MOVIE)
        fakeMediaRepository.mediaResult = RepositoryResult.Success(media)

        val savedStateHandle = SavedStateHandle(mapOf("mediaId" to "m1"))

        val viewModel = PlayerViewModel(
            savedStateHandle = savedStateHandle,
            playbackCoordinator = playbackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        viewModel.onPlayerStateChanged(PlayerLifecycleState.PLAYING, positionMs = 10_000L, durationMs = 100_000L)

        advanceTimeBy(5_000L)
        advanceUntilIdle()

        assertEquals(1, fakePlaybackRepository.savedProgressCalls.size)
        assertEquals(10_000L, fakePlaybackRepository.savedProgressCalls.first().positionMs)

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
            playbackCoordinator = playbackCoordinator,
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
            playbackCoordinator = playbackCoordinator,
            getMediaByIdUseCase = getMediaByIdUseCase,
            getEpisodeByIdUseCase = getEpisodeByIdUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            endPlaybackSessionUseCase = endPlaybackSessionUseCase
        )

        advanceUntilIdle()

        viewModel.stop()
        advanceUntilIdle()

        assertEquals(CoordinatorState.Idle, playbackCoordinator.state.value)
        assertTrue(fakeSessionRepository.endedPlaybackSession)
    }
}

private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

private class FakeProfileRepository : ProfileRepository {
    private val defaultProfile = UserProfile("profile_123", "Test User")

    override fun observeActiveProfile(): Flow<UserProfile> = flowOf(defaultProfile)
    override fun observeProfiles(): Flow<List<UserProfile>> = flowOf(listOf(defaultProfile))
    override suspend fun getActiveProfile(): RepositoryResult<UserProfile> = RepositoryResult.Success(defaultProfile)
    override suspend fun getProfile(profileId: String): RepositoryResult<UserProfile> = RepositoryResult.Success(defaultProfile)
    override suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile> = RepositoryResult.Success(profile)
    override suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile> = RepositoryResult.Success(profile)
    override suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    override suspend fun deleteProfile(profileId: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

private class FakeMediaRepository : MediaRepository {
    var mediaResult: RepositoryResult<Media> = RepositoryResult.Failure(AppError.Unknown("Not set"))
    var episodeResult: RepositoryResult<Episode> = RepositoryResult.Failure(AppError.Unknown("Not set"))

    override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> = mediaResult
    override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> = RepositoryResult.Failure(AppError.Unknown("Not implemented"))
    override suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode> = episodeResult
    override suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource> = RepositoryResult.Failure(AppError.Unknown("Not implemented"))
    override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): RepositoryResult<Map<String, Boolean>> = RepositoryResult.Success(emptyMap())
    override suspend fun search(query: String, profileId: String): RepositoryResult<List<SearchResult>> = RepositoryResult.Failure(AppError.Unknown("Not implemented"))
    override suspend fun searchStreamsByMedia(media: Media, episode: Episode?): RepositoryResult<List<StreamCandidate>> = RepositoryResult.Failure(AppError.Unknown("Not implemented"))
}

private class FakeSessionRepository : SessionRepository {
    var endedPlaybackSession = false

    override fun initialise(profile: UserProfile) {}
    override fun observeSession(): Flow<SessionState> = flowOf(SessionState(activeProfile = UserProfile("profile_123", "Test User")))
    override fun getCurrentSession(): SessionState = SessionState(activeProfile = UserProfile("profile_123", "Test User"))
    override suspend fun startPlaybackSession(request: PlaybackRequest, stream: StreamSource) {}
    override suspend fun updatePlaybackPosition(positionMs: Long) {}
    override suspend fun endPlaybackSession() { endedPlaybackSession = true }
    override suspend fun updateSearchSession(query: String, filters: Map<String, String>) {}
    override suspend fun clearSearchSession() {}
    override suspend fun clearSession() {}
}

private class FakePlaybackRepository : PlaybackRepository {
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

    override fun observeContinueWatching(profileId: String): Flow<List<WatchedItem>> = flowOf(emptyList())
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

    override suspend fun getProgress(profileId: String, mediaId: String, episodeId: String?): RepositoryResult<Long?> = RepositoryResult.Success(null)
    override suspend fun markAsCompleted(profileId: String, mediaId: String) {}
    override fun observeRecentlyPlayed(profileId: String): Flow<List<WatchedItem>> = flowOf(emptyList())
    override suspend fun recordPlayed(
        profileId: String,
        mediaId: String,
        episodeId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ) {}
    override suspend fun clearHistory(profileId: String) {}
}
