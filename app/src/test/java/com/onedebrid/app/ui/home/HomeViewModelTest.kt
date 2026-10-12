package com.onedebrid.app.ui.home

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.WatchedItem
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetContinueWatchingUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.RemoveFromContinueWatchingUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private lateinit var getActiveProfileUseCase: GetActiveProfileUseCase
    private lateinit var getContinueWatchingUseCase: GetContinueWatchingUseCase
    private lateinit var getMediaByIdUseCase: GetMediaByIdUseCase
    private lateinit var removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        activeProfileFlow = MutableSharedFlow()
        continueWatchingFlow = MutableSharedFlow()

        // Mock the UseCases directly instead of building giant Fake Repositories
        getActiveProfileUseCase = mockk()
        every { getActiveProfileUseCase() } returns activeProfileFlow

        getContinueWatchingUseCase = mockk()
        every { getContinueWatchingUseCase(any()) } returns continueWatchingFlow

        getMediaByIdUseCase = mockk()
        coEvery { getMediaByIdUseCase(any()) } returns RepositoryResult.Failure(AppError.Unknown("Not found"))

        removeFromContinueWatchingUseCase = mockk(relaxed = true)

        homeViewModel = HomeViewModel(
            getActiveProfileUseCase = getActiveProfileUseCase,
            getContinueWatchingUseCase = getContinueWatchingUseCase,
            getMediaByIdUseCase = getMediaByIdUseCase,
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
        val item = WatchedItem(
            mediaId = "m1",
            episodeId = null,
            seasonNumber = null,
            episodeNumber = null,
            positionMs = 1000L,
            durationMs = 120000L,
            lastInteractedAt = 1000000L
        )
        val media = Media(id = "m1", title = "Test Movie", type = MediaType.MOVIE)

        // Tell the mock to return our fake Media when asked for "m1"
        coEvery { getMediaByIdUseCase("m1") } returns RepositoryResult.Success(media)

        backgroundScope.launch { homeViewModel.uiState.collect {} }

        activeProfileFlow.emit(profile)
        testScheduler.advanceUntilIdle()

        continueWatchingFlow.emit(listOf(item))
        testScheduler.advanceUntilIdle()

        val state = homeViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.continueWatching.size)
        
        // Assert that the UI item correctly pairs the WatchedItem with its Media
        val uiItem = state.continueWatching.first()
        assertEquals(item, uiItem.watchedItem)
        assertEquals(media, uiItem.media)
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
}