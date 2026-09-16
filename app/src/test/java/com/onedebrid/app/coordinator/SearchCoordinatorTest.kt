package com.onedebrid.app.coordinator

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
import com.onedebrid.app.usecase.SearchMediaUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchCoordinatorTest {

    private class FakeMediaRepository : MediaRepository {
        var searchResult: RepositoryResult<List<SearchResult>> = RepositoryResult.Success(emptyList())

        override suspend fun getTrending(): RepositoryResult<List<Media>> = RepositoryResult.Success(emptyList())

        override suspend fun getMediaDetails(mediaId: String): RepositoryResult<Media> {
            return RepositoryResult.Failure(AppError.NotFound)
        }

        override suspend fun getEpisodes(mediaId: String): RepositoryResult<List<Episode>> {
            return RepositoryResult.Success(emptyList())
        }

        override suspend fun getEpisodeById(mediaId: String, episodeId: String): RepositoryResult<Episode> {
            return RepositoryResult.Failure(AppError.NotFound)
        }

        override suspend fun search(query: String, profileId: String): RepositoryResult<List<SearchResult>> {
            return searchResult
        }

        override suspend fun searchStreamsByMedia(media: Media, episode: Episode?): RepositoryResult<List<StreamCandidate>> {
            return RepositoryResult.Success(emptyList())
        }

        override suspend fun resolveStream(candidate: StreamCandidate): RepositoryResult<StreamSource> {
            return RepositoryResult.Failure(AppError.NotFound)
        }

        override suspend fun checkCacheStatus(candidates: List<StreamCandidate>): RepositoryResult<Map<String, Boolean>> {
            return RepositoryResult.Success(emptyMap())
        }
    }

    private class FakeSearchRepository : SearchRepository {
        val history = mutableListOf<Pair<String, String>>()

        override fun observeSearchHistory(profileId: String): Flow<List<String>> {
            return MutableSharedFlow()
        }

        override suspend fun getSearchHistory(profileId: String): RepositoryResult<List<String>> {
            return RepositoryResult.Success(history.filter { it.first == profileId }.map { it.second })
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

    private val fakeMediaRepository = FakeMediaRepository()
    private val fakeSearchRepository = FakeSearchRepository()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var searchMediaUseCase: SearchMediaUseCase
    private lateinit var testScope: TestScope
    private lateinit var coordinator: SearchCoordinator

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        searchMediaUseCase = SearchMediaUseCase(
            mediaRepository = fakeMediaRepository,
            searchRepository = fakeSearchRepository,
            dispatchers = dispatchers
        )
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

        fakeMediaRepository.searchResult = RepositoryResult.Success(expectedResults)

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

        fakeMediaRepository.searchResult = RepositoryResult.Failure(expectedError)

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

        fakeMediaRepository.searchResult = RepositoryResult.Success(emptyList())

        coordinator.search(query, profileId)
        advanceUntilIdle()

        coordinator.clear()

        assertEquals(SearchState.Idle, coordinator.state.value)
    }
}