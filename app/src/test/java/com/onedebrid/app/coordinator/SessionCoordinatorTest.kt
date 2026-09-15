package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.CoroutineDispatchers
import com.onedebrid.app.domain.model.UserProfile
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    private val profileRepository: ProfileRepository = mockk()
    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )

    private lateinit var testScope: TestScope
    private lateinit var coordinator: SessionCoordinator

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        coordinator = SessionCoordinator(
            profileRepository = profileRepository,
            sessionRepository = sessionRepository,
            dispatchers = dispatchers,
            scope = testScope
        )
    }

    @Test
    fun `start initialises session when active profile is emitted`() = testScope.runTest {
        val activeProfileFlow = MutableSharedFlow<UserProfile?>()
        every { profileRepository.observeActiveProfile() } returns activeProfileFlow

        coordinator.start()

        val profile = UserProfile(
            id = "profile_123",
            name = "Test Profile"
        )
        activeProfileFlow.emit(profile)
        advanceUntilIdle()

        coVerify { sessionRepository.initialise(profile) }
    }

    @Test
    fun `start ignores null active profile emissions`() = testScope.runTest {
        val activeProfileFlow = MutableSharedFlow<UserProfile?>()
        every { profileRepository.observeActiveProfile() } returns activeProfileFlow

        coordinator.start()

        activeProfileFlow.emit(null)
        advanceUntilIdle()

        coVerify(exactly = 0) { sessionRepository.initialise(any()) }
    }
}
