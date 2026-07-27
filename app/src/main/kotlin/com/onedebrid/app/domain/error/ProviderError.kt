package com.onedebrid.app.domain.error

/**
 * Represents a structured failure originating from a provider implementation.
 *
 * Providers catch raw HTTP errors, timeouts, and parsing exceptions and translate
 * them into one of these types before returning to repositories. No raw exceptions
 * or HTTP status codes cross the provider boundary.
 *
 * Defined in Technical Standards v0.1.
 */
sealed interface ProviderError {

    /**
     * The API key or session token was rejected.
     * The user likely needs to re-authenticate.
     */
    data object AuthenticationFailed : ProviderError

    /**
     * The provider's rate limit or quota was exceeded.
     *
     * [retryAfterSeconds] is included when the provider specifies a backoff window.
     * Null means the retry window is unknown.
     */
    data class RateLimited(val retryAfterSeconds: Long? = null) : ProviderError

    /**
     * The requested content or link does not exist on this provider.
     * This is a normal result for cache misses — not a failure to handle as critical.
     */
    data object NotFound : ProviderError

    /**
     * The provider API is offline or returning server-side errors (5xx).
     * Retry may succeed after a delay.
     */
    data object ServiceUnavailable : ProviderError

    /**
     * No internet connection, or the connection timed out before a response arrived.
     */
    data object NetworkError : ProviderError

    /**
     * The provider returned a response, but it could not be parsed.
     * Usually indicates an API change on the provider's side.
     *
     * [cause] is retained for logging purposes only. It must not be shown to the user.
     */
    data class ParsingError(val cause: Throwable) : ProviderError
}