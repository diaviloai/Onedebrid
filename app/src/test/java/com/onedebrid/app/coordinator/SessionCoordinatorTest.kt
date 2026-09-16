package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackRequest
import com.onedebrid.app.domain.model.SessionState
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    private class FakeProfileRepository : ProfileRepository {
        val activeProfileFlow = MutableSharedFlow<UserProfile>()

        override fun observeProfiles(): Flow<List<UserProfile>> = MutableSharedFlow()
        override fun observeActiveProfile(): Flow<UserProfile> = activeProfileFlow
        override suspend fun getProfile(profileId: String): RepositoryResult<UserProfile> = RepositoryResult.Failure(AppError.Unknown("Not found"))
        override suspend fun getActiveProfile(): RepositoryResult<UserProfile> = RepositoryResult.Failure(AppError.Unknown("Not found"))
        override suspend fun createProfile(profile: UserProfile): RepositoryResult<UserProfile> = RepositoryResult.Success(profile)
        override suspend fun updateProfile(profile: UserProfile): RepositoryResult<UserProfile> = RepositoryResult.Success(profile)
        override suspend fun deleteProfile(profileId: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun setActiveProfile(profileId: String): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }

    private class FakeSessionRepository : SessionRepository {
        val initialisedProfiles = mutableListOf<UserProfile>()

        override fun initialise(profile: UserProfile) {
            initialisedProfiles.add(profile)
        }

        override fun observeSession(): Flow<SessionState> = MutableSharedFlow()
        override fun getCurrentSession(): SessionState? = null
        override suspend fun startPlaybackSession(request: PlaybackRequest, stream: StreamSource) {}
        override suspend fun updatePlaybackPosition(positionMs: Long) {}
        override suspend fun endPlaybackSession() {}
        override suspend fun updateSearchSession(query: String, filters: Map<String, String>) {}
        override suspend fun clearSearchSession() {}
        override suspend fun clearSession() {}
    }

    private class TestCoroutineDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private val fakeProfileRepository = FakeProfileRepository()
    private val fakeSessionRepository = FakeSessionRepository()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestCoroutineDispatchers(testDispatcher)

    private lateinit var testScope: TestScope
    private lateinit var coordinator: SessionCoordinator

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        coordinator = SessionCoordinator(
            profileRepository = fakeProfileRepository,
            sessionRepository = fakeSessionRepository,
            dispatchers = dispatchers,
            scope = testScope
        )
    }

    @Test
    fun `start initialises session when active profile is emitted`() = testScope.runTest {
        coordinator.start()

        val profile = UserProfile(
            id = "profile_123",
            name = "Test Profile"
        )
        fakeProfileRepository.activeProfileFlow.emit(profile)
        advanceUntilIdle()

        assertEquals(1, fakeSessionRepository.initialisedProfiles.size)
        assertEquals(profile, fakeSessionRepository.initialisedProfiles.first())

        testScope.cancel()
    }
}
