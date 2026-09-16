package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val activeProfileFlow = MutableSharedFlow<UserProfile?>()

        override fun observeActiveProfile(): Flow<UserProfile?> {
            return activeProfileFlow
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val initialisedProfiles = mutableListOf<UserProfile>()

        override suspend fun initialise(profile: UserProfile) {
            initialisedProfiles.add(profile)
        }
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
    }

    @Test
    fun `start ignores null active profile emissions`() = testScope.runTest {
        coordinator.start()

        fakeProfileRepository.activeProfileFlow.emit(null)
        advanceUntilIdle()

        assertEquals(0, fakeSessionRepository.initialisedProfiles.size)
    }
}
