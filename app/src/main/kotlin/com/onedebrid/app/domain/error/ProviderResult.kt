package com.onedebrid.app.domain.error

/**
 * Represents the outcome of a provider operation.
 *
 * Every provider interface method returns ProviderResult<T> rather than
 * throwing exceptions or returning nullable values. This keeps error handling
 * explicit and forces callers to acknowledge both cases.
 *
 * Usage:
 *   when (result) {
 *       is ProviderResult.Success -> result.data
 *       is ProviderResult.Failure -> result.error
 *   }
 */
sealed interface ProviderResult<out T> {

    data class Success<out T>(val data: T) : ProviderResult<T>

    data class Failure(val error: ProviderError) : ProviderResult<Nothing>
}

/** Convenience: wrap a value in Success. */
fun <T> T.asSuccess(): ProviderResult<T> = ProviderResult.Success(this)

/** Convenience: wrap a ProviderError in Failure. */
fun ProviderError.asFailure(): ProviderResult<Nothing> = ProviderResult.Failure(this)