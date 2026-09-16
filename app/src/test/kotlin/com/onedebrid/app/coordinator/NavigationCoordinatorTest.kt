package com.onedebrid.app.coordinator

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
    fun `navigateToPlayer emits expected PlayerNavArgs`() = testScope.runTest {
        val emittedArgs = mutableListOf<PlayerNavArgs>()

        val job = coordinatorScope.launch {
            navigationCoordinator.navigateToPlayer.collect { args ->
                emittedArgs.add(args)
            }
        }

        val expectedArgs = PlayerNavArgs(
            mediaId = "media_123",
            episodeId = "ep_456",
            resumeMs = 5000L,
            preferredSource = null
        )

        navigationCoordinator.navigateToPlayer(expectedArgs)
        advanceUntilIdle()

        assertEquals(1, emittedArgs.size)
        assertEquals(expectedArgs, emittedArgs.first())

        job.cancel()
    }
}
