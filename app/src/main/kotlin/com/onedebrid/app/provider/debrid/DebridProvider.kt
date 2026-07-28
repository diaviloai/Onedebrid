package com.onedebrid.app.provider.debrid

import com.onedebrid.app.domain.error.ProviderResult
import com.onedebrid.app.domain.model.StreamSource

/**
 * Contract for all debrid service integrations.
 *
 * Responsible for account verification, cache inspection, and stream
 * resolution. No other system communicates with a debrid service directly.
 *
 * Defined in Provider Architecture v0.1.
 */
interface DebridProvider {

    /** A stable identifier for this provider, e.g. "real_debrid". */
    val id: String

    /** Human-readable display name, e.g. "Real-Debrid". */
    val displayName: String

    /**
     * Verifies that the stored credentials are valid and the account
     * is in good standing.
     *
     * Returns [AccountInfo] on success, or a [ProviderError] if
     * credentials are missing, expired, or the service is unreachable.
     */
    suspend fun verifyAccount(): ProviderResult<AccountInfo>

    /**
     * Checks whether any of the provided hashes are available in the
     * debrid service's cache for instant streaming.
     *
     * [hashes] is a list of torrent info-hashes (40-character hex strings).
     *
     * Returns a map of hash → availability. Hashes absent from the map
     * are not cached. A provider error is returned if the check itself fails.
     */
    suspend fun checkCache(hashes: List<String>): ProviderResult<Map<String, Boolean>>

    /**
     * Resolves a cached hash or magnet link into a direct streaming URL.
     *
     * This is the core operation of the debrid layer. The returned
     * [StreamSource] contains a ready-to-play URL that can be handed
     * directly to the player.
     *
     * Returns [ProviderError.NotFound] if the hash is not cached.
     * Returns [ProviderError.AuthenticationFailed] if the account
     * is no longer valid.
     */
    suspend fun resolveStream(hash: String): ProviderResult<StreamSource>
}

/**
 * Basic account status returned by [DebridProvider.verifyAccount].
 *
 * Kept minimal intentionally. Provider-specific account details
 * (expiry dates, point balances) belong in provider-specific models,
 * not in the shared domain.
 */
data class AccountInfo(
    val username: String,
    val isActive: Boolean,
    val expiresAt: Long? = null  // epoch millis, null if no expiry
)