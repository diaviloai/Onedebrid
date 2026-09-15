package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.SearchResult
import com.onedebrid.app.usecase.SearchMediaUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchCoordinatorTest {

    private val searchMediaUseCase: SearchMediaUseCase = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )

    private lateinit var testScope: TestScope
    private lateinit var coordinator: SearchCoordinator

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        coordinator = SearchCoordinator(
            searchMediaUseCase = searchMediaUseCase,
            dispatchers = dispatchers,
            scope = testScope
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(SearchState.Idle, coordinator.state.value)
    }

    @Test
    fun `search updates state to Results on success`() = testScope.runTest {
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

        whenever(searchMediaUseCase(query, profileId)).thenReturn(
            RepositoryResult.Success(expectedResults)
        )

        coordinator.search(query, profileId)
        advanceUntilIdle()

        val currentState = coordinator.state.value
        assertTrue(currentState is SearchState.Results)
        assertEquals(expectedResults, (currentState as SearchState.Results).results)
    }

    @Test
    fun `search updates state to Error on failure`() = testScope.runTest {
        val query = "Unknown"
        val profileId = "profile_1"
        val expectedError = AppError.NoNetworkConnection

        whenever(searchMediaUseCase(query, profileId)).thenReturn(
            RepositoryResult.Failure(expectedError)
        )

        coordinator.search(query, profileId)
        advanceUntilIdle()

        val currentState = coordinator.state.value
        assertTrue(currentState is SearchState.Error)
        assertEquals(expectedError, (currentState as SearchState.Error).error)
    }

    @Test
    fun `clear resets state to Idle`() = testScope.runTest {
        val query = "Inception"
        val profileId = "profile_1"

        whenever(searchMediaUseCase(query, profileId)).thenReturn(
            RepositoryResult.Success(emptyList())
        )

        coordinator.search(query, profileId)
        advanceUntilIdle()

        coordinator.clear()

        assertEquals(SearchState.Idle, coordinator.state.value)
    }
}
