package com.onedebrid.app.ui.home

import com.onedebrid.app.data.repository.PlaybackRepository
import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetContinueWatchingUseCase
import com.onedebrid.app.usecase.RemoveFromContinueWatchingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileFlow: MutableSharedFlow<UserProfile>
    private lateinit var continueWatchingFlow: MutableSharedFlow<List<WatchedItem>>
    private lateinit var fakePlaybackRepository: FakePlaybackRepository

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        activeProfileFlow = MutableSharedFlow()
        continueWatchingFlow = MutableSharedFlow()
        fakePlaybackRepository = FakePlaybackRepository(continueWatchingFlow)

        val profileRepository = FakeProfileRepository(activeProfileFlow)
        val getActiveProfileUseCase = GetActiveProfileUseCase(profileRepository)
        val getContinueWatchingUseCase = GetContinueWatchingUseCase(fakePlaybackRepository)
        val removeFromContinueWatchingUseCase = RemoveFromContinueWatchingUseCase(fakePlaybackRepository)

        homeViewModel = HomeViewModel(
            getActiveProfileUseCase = getActiveProfileUseCase,
            getContinueWatchingUseCase = getContinueWatchingUseCase,
            removeFromContinueWatchingUseCase = removeFromContinueWatchingUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes continue watching list on active profile emission`() = runTest(testDispatcher) {
        val profile = UserProfile(id = "p1", name = "Test Profile")
        val items = listOf(
            WatchedItem(
                mediaId = "m1",
                episodeId = null,
                seasonNumber = null,
                episodeNumber = null,
                positionMs = 1000L,
                durationMs = 120000L,
                lastInteractedAt = 1000000L
            )
        )

        backgroundScope.launch { homeViewModel.uiState.collect {} }

        activeProfileFlow.emit(profile)
        testScheduler.advanceUntilIdle()

        continueWatchingFlow.emit(items)
        testScheduler.advanceUntilIdle()

        val state = homeViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(items, state.continueWatching)
    }

    @Test
    fun `onItemClick emits navigation args channel event`() = runTest(testDispatcher) {
        val profile = UserProfile(id = "p1", name = "Test Profile")
        val item = WatchedItem(
            mediaId = "m1",
            episodeId = "e1",
            seasonNumber = 1,
            episodeNumber = 1,
            positionMs = 5000L,
            durationMs = 120000L,
            lastInteractedAt = 1000000L
        )

        activeProfileFlow.emit(profile)
        testScheduler.advanceUntilIdle()

        homeViewModel.onItemClick(item)

        val navArgs = homeViewModel.navigateToPlayer.first()
        assertEquals("m1", navArgs.mediaId)
        assertEquals("e1", navArgs.episodeId)
        assertEquals(5000L, navArgs.resumeMs)
    }

    private class FakeProfileRepository(
        private val activeProfileFlow: Flow<UserProfile>
    ) : ProfileRepository {
        override fun observeProfiles(): Flow<List<UserProfile>> = MutableSharedFlow()
        override fun observeActiveProfile(): Flow<UserProfile> = activeProfileFlow
        override suspend fun getProfile(profileId: String): RepositoryResult<UserProfile> =
            RepositoryResult.Failure(AppError.Unknown("Not found"))
        override suspend fun getActiveProfile(): RepositoryResult<UserProfile> =
            RepositoryResult.Failure(AppError.Unknown("Not found"))
        override suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile> =
            RepositoryResult.Success(profile)
        override suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile> =
            RepositoryResult.Success(profile)
        override suspend fun deleteProfile(profileId: String): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
        override suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class FakePlaybackRepository(
        private val continueWatchingFlow: Flow<List<WatchedItem>>
    ) : PlaybackRepository {
        val removedItems = mutableListOf<Pair<String, String>>()

        override fun observeContinueWatching(profileId: String): Flow<List<WatchedItem>> = continueWatchingFlow

        override suspend fun removeFromContinueWatching(profileId: String, mediaId: String) {
            removedItems.add(profileId to mediaId)
        }

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
        ) {}

        override suspend fun clearHistory(profileId: String) {}
    }
}
