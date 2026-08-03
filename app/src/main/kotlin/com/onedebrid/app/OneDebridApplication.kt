package com.onedebrid.app

import android.app.Application
import com.onedebrid.app.coordinator.SessionCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OneDebridApplication : Application() {

    @Inject
    lateinit var sessionCoordinator: SessionCoordinator

    override fun onCreate() {
        super.onCreate()
        sessionCoordinator.start()
    }
}