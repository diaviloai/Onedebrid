package com.onedebrid.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for OneDebrid.
 *
 * The @HiltAndroidApp annotation triggers Hilt's code generation at build time,
 * creating the application-level dependency injection component that all other
 * Hilt components in the app depend on.
 *
 * This class must be declared in AndroidManifest.xml via android:name=".OneDebridApplication"
 * for Android to use it as the application entry point instead of the default Application class.
 *
 * No logic belongs here beyond what Hilt requires. Application-level initialisation
 * that genuinely needs to run at startup (logging, crash reporting, etc.) can be
 * added to onCreate() when the need arises.
 */
@HiltAndroidApp
class OneDebridApplication : Application()