package com.onedebrid.app.ui.search

import com.onedebrid.app.coordinator.SearchCoordinator
import com.onedebrid.app.coordinator.SearchState
import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.usecase.ClearSearchHistoryUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetSearchHistoryUseCase
import com.onedebrid.app.usecase.SearchMediaUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var mockMediaRepository: MediaRepository
    private lateinit var mockProfileRepository: ProfileRepository
    private lateinit var fakeSearchRepository: FakeSearchRepository
    private lateinit var activeProfileFlow: MutableSharedFlow<UserProfile>

    private lateinit var searchCoordinator: SearchCoordinator
    private lateinit var searchViewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockMediaRepository = mockk()
        mockProfileRepository = mockk()
        fakeSearchRepository = FakeSearchRepository()
        activeProfileFlow = MutableSharedFlow()

        // Default mock behavior
        coEvery { mockProfileRepository.getProfile(any()) } returns RepositoryResult.Failure(AppError.Unknown("Not found"))
        every { mockProfileRepository.observeActiveProfile() } returns activeProfileFlow

        val searchMediaUseCase = SearchMediaUseCase(
            mediaRepository = mockMediaRepository,
            searchRepository = fakeSearchRepository,
            profileRepository = mockProfileRepository,
            dispatchers = dispatchers
        )
        val coordinatorScope = CoroutineScope(testDispatcher + SupervisorJob())

        searchCoordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )

        val getActiveProfileUseCase = GetActiveProfileUseCase(
            profileRepository = mockProfileRepository
        )
        val getSearchHistoryUseCase = GetSearchHistoryUseCase(
            searchRepository = fakeSearchRepository
        )
        val clearSearchHistoryUseCase = ClearSearchHistoryUseCase(
            searchRepository = fakeSearchRepository,
            dispatchers = dispatchers
        )

        searchViewModel = SearchViewModel(
            searchCoordinator = searchCoordinator,
            getSearchHistoryUseCase = getSearchHistoryUseCase,
            clearSearchHistoryUseCase = clearSearchHistoryUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `active profile emission updates activeProfileId in UiState and loads history`() = runTest(testDispatcher) {
        val testProfile = UserProfile(id = "p1", name = "Test Profile")

        backgroundScope.launch { searchViewModel.uiState.collect {} }

        activeProfileFlow.emit(testProfile)
        testScheduler.advanceUntilIdle()

        assertEquals("p1", searchViewModel.uiState.value.activeProfileId)

        fakeSearchRepository.historyFlow.emit(listOf("Inception", "Interstellar"))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("Inception", "Interstellar"), searchViewModel.uiState.value.searchHistory)
    }

    @Test
    fun `search delegates to SearchCoordinator when profile is active`() = runTest(testDispatcher) {
        val testProfile = UserProfile(id = "p1", name = "Test Profile")
        val expectedResults = listOf(
            SearchResult(
                media = Media(id = "m1", title = "Inception", type = MediaType.MOVIE, year = 2010),
                candidates = emptyList(),
                sourceProvider = "stub"
            )
        )
        
        coEvery { mockMediaRepository.search(any(), any(), any()) } returns RepositoryResult.Success(expectedResults)

        backgroundScope.launch { searchViewModel.uiState.collect {} }

        activeProfileFlow.emit(testProfile)
        testScheduler.advanceUntilIdle()

        searchViewModel.search("Inception")
        testScheduler.advanceUntilIdle()

        val state = searchViewModel.uiState.value.searchState
        assertEquals(SearchState.Results(expectedResults), state)
    }

    @Test
    fun `clearSearch resets SearchCoordinator state to Idle`() = runTest(testDispatcher) {
        val testProfile = UserProfile(id = "p1", name = "Test Profile")
        
        coEvery { mockMediaRepository.search(any(), any(), any()) } returns RepositoryResult.Success(emptyList())

        backgroundScope.launch { searchViewModel.uiState.collect {} }

        activeProfileFlow.emit(testProfile)
        testScheduler.advanceUntilIdle()

        searchViewModel.search("Inception")
        testScheduler.advanceUntilIdle()

        searchViewModel.clearSearch()
        testScheduler.advanceUntilIdle()

        assertEquals(SearchState.Idle, searchViewModel.uiState.value.searchState)
    }

    private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class FakeSearchRepository : SearchRepository {
        val historyFlow = MutableSharedFlow<List<String>>()

        override fun observeSearchHistory(profileId: String): Flow<List<String>> = historyFlow

        override suspend fun addSearchQuery(query: String, profileId: String) {}
        override suspend fun removeSearchQuery(query: String, profileId: String) {}
        override suspend fun clearSearchHistory(profileId: String) {}
    }
}