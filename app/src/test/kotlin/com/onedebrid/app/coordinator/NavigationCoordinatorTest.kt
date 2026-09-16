package com.onedebrid.app.coordinator

import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.ui.navigation.PlayerNavArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testSupervisorJob = SupervisorJob()
    private val coordinatorScope = CoroutineScope(testDispatcher + testSupervisorJob)

    private lateinit var navigationCoordinator: NavigationCoordinator

    @Before
    fun setup() {
        navigationCoordinator = NavigationCoordinator()
    }

    @After
    fun tearDown() {
        testSupervisorJob.cancel()
    }

    @Test
    fun `navigateToPlayer emits expected NavigationTarget or NavArgs`() = testScope.runTest {
        val emittedEvents = mutableListOf<NavigationTarget>()

        val job = coordinatorScope.launch {
            navigationCoordinator.navigationEvents.collect { target ->
                emittedEvents.add(target)
            }
        }

        val target = NavigationTarget.Player(
            mediaType = MediaType.MOVIE,
            mediaId = "media_123",
            episodeId = "ep_456",
            preferredSource = "source_1"
        )

        navigationCoordinator.navigateTo(target)
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(target, emittedEvents.first())

        job.cancel()
    }
}
