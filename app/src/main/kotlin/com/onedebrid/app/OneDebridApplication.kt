package com.onedebrid.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.onedebrid.app.coordinator.SessionCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OneDebridApplication : Application(), Configuration.Provider {

    // Injects Hilt's worker factory so WorkManager can pass DAOs and Repositories into our Workers
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionCoordinator: SessionCoordinator

    override fun onCreate() {
        super.onCreate()
        sessionCoordinator.start()
    }

    // Tells Android's WorkManager to use Hilt to construct its background workers
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}