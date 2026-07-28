package com.onedebrid.app.data.repository

import com.onedebrid.app.domain.error.AppError

/**
 * Sealed return type for all repository one-shot operations.
 *
 * Repositories translate lower-level ProviderError values into AppError before
 * returning. Callers in the Use Case layer work only with AppError — they never
 * see ProviderError directly.
 *
 * This mirrors ProviderResult in shape but belongs to the data layer and uses
 * the application-level error type.
 */
sealed interface RepositoryResult<out T> {

    data class Success<out T>(val data: T) : RepositoryResult<T>

    data class Failure(val error: AppError) : RepositoryResult<Nothing>
}

fun <T> RepositoryResult<T>.asSuccess(): RepositoryResult.Success<T>? =
    this as? RepositoryResult.Success<T>

fun <T> RepositoryResult<T>.asFailure(): RepositoryResult.Failure? =
    this as? RepositoryResult.Failure