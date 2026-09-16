package com.onedebrid.app.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        navigationCoordinator = NavigationCoordinator(scope = coordinatorScope)
    }

    @After
    fun tearDown() {
        testSupervisorJob.cancel()
    }

    @Test
    fun `navigateTo emits target destination`() = testScope.runTest {
        val emittedEvents = mutableListOf<NavigationTarget>()
        
        val job = coordinatorScope.launch {
            navigationCoordinator.navigationEvents.collect {
                emittedEvents.add(it)
            }
        }

        val destination = NavigationTarget.Details(mediaId = "media_123")
        navigationCoordinator.navigateTo(destination)
        advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(destination, emittedEvents.first())

        job.cancel()
    }
}
