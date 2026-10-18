package com.onedebrid.app.coordinator

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
import com.onedebrid.app.usecase.SearchMediaUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchCoordinatorTest {

    private class FakeSearchRepository : SearchRepository {
        val history = mutableListOf<Pair<String, String>>()

        override fun observeSearchHistory(profileId: String): Flow<List<String>> {
            return MutableSharedFlow()
        }

        override suspend fun addSearchQuery(query: String, profileId: String) {
            history.add(profileId to query)
        }

        override suspend fun removeSearchQuery(query: String, profileId: String) {
            history.removeAll { it.first == profileId && it.second == query }
        }

        override suspend fun clearSearchHistory(profileId: String) {
            history.removeAll { it.first == profileId }
        }
    }

    private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private lateinit var mockMediaRepository: MediaRepository
    private lateinit var mockProfileRepository: ProfileRepository
    private val fakeSearchRepository = FakeSearchRepository()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var searchMediaUseCase: SearchMediaUseCase

    @Before
    fun setUp() {
        mockMediaRepository = mockk()
        mockProfileRepository = mockk()

        // Default mock behavior
        coEvery { mockProfileRepository.getProfile(any()) } returns RepositoryResult.Failure(AppError.Unknown("Not found"))

        searchMediaUseCase = SearchMediaUseCase(
            mediaRepository = mockMediaRepository,
            searchRepository = fakeSearchRepository,
            profileRepository = mockProfileRepository,
            dispatchers = dispatchers
        )
    }

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        val coordinatorJob = SupervisorJob()
        val coordinatorScope = CoroutineScope(testDispatcher + coordinatorJob)

        val coordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )

        assertEquals(SearchState.Idle, coordinator.state.value)

        coordinatorScope.cancel()
    }

    @Test
    fun `search updates state to Results on success`() = runTest(testDispatcher) {
        val coordinatorJob = SupervisorJob()
        val coordinatorScope = CoroutineScope(testDispatcher + coordinatorJob)

        val coordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )

        backgroundScope.launch { coordinator.state.collect {} }

        val query = "Inception"
        val profileId = "profile_1"
        val expectedResults = listOf(
            SearchResult(
                media = Media(
                    id = "m1",
                    title = "Inception",
                    type = MediaType.MOVIE,
                    year = 2010
                ),
                candidates = emptyList(),
                sourceProvider = "stub"
            )
        )

        coEvery { mockMediaRepository.search(any(), any(), any()) } returns RepositoryResult.Success(expectedResults)

        coordinator.search(query, profileId)
        advanceUntilIdle()

        val currentState = coordinator.state.value
        assertTrue("Expected SearchState.Results but was $currentState", currentState is SearchState.Results)
        assertEquals(expectedResults, (currentState as SearchState.Results).results)

        coordinatorScope.cancel()
    }

    @Test
    fun `search updates state to Error on failure`() = runTest(testDispatcher) {
        val coordinatorJob = SupervisorJob()
        val coordinatorScope = CoroutineScope(testDispatcher + coordinatorJob)

        val coordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )

        backgroundScope.launch { coordinator.state.collect {} }

        val query = "Unknown"
        val profileId = "profile_1"
        val expectedError = AppError.NoNetworkConnection

        coEvery { mockMediaRepository.search(any(), any(), any()) } returns RepositoryResult.Failure(expectedError)

        coordinator.search(query, profileId)
        advanceUntilIdle()

        val currentState = coordinator.state.value
        assertTrue("Expected SearchState.Error but was $currentState", currentState is SearchState.Error)
        assertEquals(expectedError, (currentState as SearchState.Error).error)

        coordinatorScope.cancel()
    }

    @Test
    fun `clear resets state to Idle`() = runTest(testDispatcher) {
        val coordinatorJob = SupervisorJob()
        val coordinatorScope = CoroutineScope(testDispatcher + coordinatorJob)

        val coordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = coordinatorScope
        )

        backgroundScope.launch { coordinator.state.collect {} }

        val query = "Inception"
        val profileId = "profile_1"

        coEvery { mockMediaRepository.search(any(), any(), any()) } returns RepositoryResult.Success(emptyList())

        coordinator.search(query, profileId)
        advanceUntilIdle()

        coordinator.clear()

        assertEquals(SearchState.Idle, coordinator.state.value)

        coordinatorScope.cancel()
    }
}