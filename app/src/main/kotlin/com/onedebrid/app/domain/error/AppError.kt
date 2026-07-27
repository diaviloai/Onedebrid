package com.onedebrid.app.domain.error

/**
 * Represents a structured failure at the application logic layer.
 *
 * Use Cases and Coordinators translate ProviderErrors (and other failures)
 * into AppErrors before returning to ViewModels. ViewModels never receive
 * raw ProviderErrors.
 *
 * Severity guides how the UI presents the error — see UI/UX Design v0.1.
 */
sealed interface AppError {

    /** Indicates whether the operation that failed can be retried by the user. */
    val isRecoverable: Boolean

    // ── Playback ──────────────────────────────────────────────────────────────

    /**
     * No cached stream was found for the selected content across all configured
     * debrid providers. The content exists but is not available for instant play.
     */
    data object NoCachedStreamAvailable : AppError {
        override val isRecoverable: Boolean = false
    }

    /**
     * Stream resolution succeeded but the returned URL could not be opened
     * by the player. May be transient.
     */
    data object StreamResolutionFailed : AppError {
        override val isRecoverable: Boolean = true
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * The user's debrid account credentials are missing or have expired.
     * Requires user action to re-authenticate.
     */
    data object NotAuthenticated : AppError {
        override val isRecoverable: Boolean = false
    }

    // ── Network ───────────────────────────────────────────────────────────────

    /**
     * The operation failed because no network connection was available.
     * Recoverable once connectivity is restored.
     */
    data object NoNetworkConnection : AppError {
        override val isRecoverable: Boolean = true
    }

    /**
     * All configured providers for this operation are currently unavailable.
     * May recover on retry after a delay.
     */
    data object AllProvidersUnavailable : AppError {
        override val isRecoverable: Boolean = true
    }

    // ── Local Storage ─────────────────────────────────────────────────────────

    /**
     * A local database operation failed.
     *
     * [cause] is retained for logging. Not shown to the user directly.
     */
    data class LocalStorageError(val cause: Throwable) : AppError {
        override val isRecoverable: Boolean = false
    }

    // ── General ───────────────────────────────────────────────────────────────

    /**
     * An error that does not fit a more specific category.
     *
     * Use sparingly. Prefer adding a specific type over using this.
     * [message] is for internal logging only.
     */
    data class Unknown(val message: String) : AppError {
        override val isRecoverable: Boolean = false
    }
}