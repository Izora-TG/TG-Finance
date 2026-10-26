package com.tgfinance.server.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * Brute-force protection for the login: after [maxAttempts] failures within the window a key
 * (client IP or username) is locked for [lockMillis]. Kept in memory – fine for a single
 * self-hosted instance.
 */
class LoginRateLimiter(
    private val maxAttempts: Int = 5,
    private val lockMillis: Long = 15 * 60_000L,
    private val windowMillis: Long = 15 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val failures: Int, val firstFailureAt: Long, val lockedUntil: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Milliseconds until the key may try again, or 0 if it is not locked. */
    fun retryAfter(key: String): Long {
        val entry = entries[key] ?: return 0
        val now = clock()
        return (entry.lockedUntil - now).coerceAtLeast(0)
    }

    fun recordFailure(key: String) {
        val now = clock()
        entries.compute(key) { _, current ->
            val base = if (current == null || now - current.firstFailureAt > windowMillis) Entry(0, now, 0) else current
            val failures = base.failures + 1
            base.copy(failures = failures, lockedUntil = if (failures >= maxAttempts) now + lockMillis else base.lockedUntil)
        }
    }

    fun recordSuccess(key: String) {
        entries.remove(key)
    }
}
