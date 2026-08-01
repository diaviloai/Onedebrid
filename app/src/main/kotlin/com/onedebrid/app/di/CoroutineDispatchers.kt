package com.onedebrid.app.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over coroutine dispatchers.
 *
 * Injecting this interface rather than referencing Dispatchers.* directly
 * allows unit tests to substitute TestCoroutineDispatcher (or UnconfinedTestDispatcher)
 * without needing to touch the production code path.
 *
 * Required by Technical Standards — hardcoded dispatchers are prohibited in
 * Use Cases, Repositories, and ViewModels.
 */
interface CoroutineDispatchers {
    /** Main thread. Used for UI updates and StateFlow emissions. */
    val main: CoroutineDispatcher

    /** IO-optimised thread pool. Used for Room, Retrofit, and file operations. */
    val io: CoroutineDispatcher

    /** CPU-optimised thread pool. Used for sorting, filtering, and parsing work. */
    val default: CoroutineDispatcher
}

/**
 * Production implementation. Bound to real Dispatchers by Hilt.
 */
@Singleton
class DefaultCoroutineDispatchers @Inject constructor() : CoroutineDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}