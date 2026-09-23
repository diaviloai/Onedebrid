package com.onedebrid.app.ui.search

import com.onedebrid.app.coordinator.SearchCoordinator
import com.onedebrid.app.coordinator.SearchState
import com.onedebrid.app.data.repository.MediaRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SearchRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.usecase.ClearSearchHistoryUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetSearchHistoryUseCase
import com.onedebrid.app.usecase.SearchMediaUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private lateinit var fakeMediaRepository: FakeMediaRepository
    private lateinit var fakeSearchRepository: FakeSearchRepository
    private lateinit var activeProfileFlow: MutableSharedFlow<UserProfile>
    private lateinit var searchHistoryFlow: MutableSharedFlow<List<String>>

    private lateinit var searchCoordinator: SearchCoordinator
    private lateinit var searchViewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeMediaRepository = FakeMediaRepository()
        fakeSearchRepository = FakeSearchRepository()
        activeProfileFlow = MutableSharedFlow()
        searchHistoryFlow = MutableSharedFlow()

        val searchMediaUseCase = SearchMediaUseCase(fakeMediaRepository, fakeSearchRepository, dispatchers)
        val coordinatorScope = CoroutineScope(testDispatcher + SupervisorJob())

        searchCoordinator = SearchCoordinator(searchMediaUseCase, dispatchers, coordinatorScope)

        val getActiveProfileUseCase = GetActiveProfileUseCase(FakeProfileRepository(activeProfileFlow))
        val getSearchHistoryUseCase = GetSearchHistoryUseCase(fakeSearchRepository)
        val clearSearchHistoryUseCase = ClearSearchHistoryUseCase(fakeSearchRepository)

        searchViewModel = SearchViewModel(
            searchCoordinator = searchCoordinator,
            getSearchHistoryUseCase = getSearchHistoryUseCase,
            clearSearchHistoryUseCase = clearSearchHistoryUseCase,
            getActiveProfileUseCase = getActiveProfileUseCase,
            dispatchers = dispatchers
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
        fakeMediaRepository.searchResult = RepositoryResult.Success(expectedResults)

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
        fakeMediaRepository.searchResult = RepositoryResult.Success(emptyList())

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

    private class FakeMediaRepository : MediaRepository {
        var searchResult: RepositoryResult<List<SearchResult>> = RepositoryResult.Success(emptyList())

        override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> =
            RepositoryResult.Failure(AppError.Unknown("Not found"))

        override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> =
            RepositoryResult.Success(emptyList())

        override suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode> =
            RepositoryResult.Failure(AppError.Unknown("Not found"))

        override suspend fun search(query: String, profileId: String): RepositoryResult<List<SearchResult>> =
            searchResult

        override suspend fun searchStreamsByMedia(media: Media, episode: Episode?): RepositoryResult<List<StreamCandidate>> =
            RepositoryResult.Success(emptyList())

        override suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource> =
            RepositoryResult.Failure(AppError.Unknown("Not found"))

        override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): RepositoryResult<Map<String, Boolean>> =
            RepositoryResult.Success(emptyMap())
    }

    private class FakeSearchRepository : SearchRepository {
        val historyFlow = MutableSharedFlow<List<String>>()

        override fun observeSearchHistory(profileId: String): Flow<List<String>> = historyFlow

        override suspend fun addSearchQuery(profileId: String, query: String) {}
        override suspend fun removeSearchQuery(profileId: String, query: String) {}
        override suspend fun clearSearchHistory(profileId: String) {}
    }

    private class FakeProfileRepository(
        private val activeProfileFlow: Flow<UserProfile>
    ) : com.onedebrid.app.data.repository.ProfileRepository {
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
}
