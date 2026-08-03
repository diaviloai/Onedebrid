package com.onedebrid.app.coordinator

import com.onedebrid.app.data.repository.ProfileRepository
import com.onedebrid.app.data.repository.SessionRepository
import com.onedebrid.app.di.ApplicationScope
import com.onedebrid.app.di.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the application session lifecycle.
 *
 * Observes the active profile and keeps the session initialised
 * whenever the active profile changes. Must be started once from
 * OneDebridApplication.onCreate().
 *
 * All other Coordinators and Use Cases that depend on session state
 * can safely assume the session is initialised after start() is called.
 */
@Singleton
class SessionCoordinator @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val dispatchers: CoroutineDispatchers,
    @ApplicationScope private val scope: CoroutineScope
) {

    /**
     * Start observing the active profile and initialising the session.
     *
     * Safe to call multiple times — only the first call has effect
     * because the Flow collection is launched into the application scope
     * which lives for the process lifetime.
     */
    fun start() {
        scope.launch(dispatchers.default) {
            profileRepository.observeActiveProfile()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { profile ->
                    sessionRepository.initialise(profile)
                }
        }
    }
}