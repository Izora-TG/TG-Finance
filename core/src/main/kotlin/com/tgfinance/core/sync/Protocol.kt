package com.tgfinance.core.sync

import kotlinx.serialization.Serializable

/**
 * Wire format shared by the Android client and the Ktor server.
 *
 * Every synced row travels as a [SyncRecord]: the metadata needed for conflict
 * resolution stays readable for the server, the entity itself is an opaque [payload]
 * (plain JSON or, with end-to-end encryption, an encrypted blob).
 */
@Serializable
data class SyncRecord(
    val type: String,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
    val payload: String,
) {
    val key: RecordKey get() = RecordKey(type, id)
}

@Serializable
data class RecordKey(
    val type: String,
    val id: String,
)

@Serializable
data class PushRequest(
    val deviceId: String,
    val records: List<SyncRecord>,
)

@Serializable
data class PushResponse(
    /** Records that the server stored. */
    val accepted: List<RecordKey>,
    /** Records the server refused because it holds a newer version; that version is returned. */
    val rejected: List<SyncRecord> = emptyList(),
)

@Serializable
data class PullResponse(
    val records: List<SyncRecord>,
    /** Opaque, monotonically increasing server cursor; pass it to the next pull. */
    val cursor: Long,
    val hasMore: Boolean,
)

@Serializable
data class CredentialsRequest(
    val username: String,
    val password: String,
)

@Serializable
data class TokenResponse(
    val token: String,
    val expiresAt: Long,
    val username: String,
)

@Serializable
data class UserInfo(
    val username: String,
    /** Base64 salt for deriving the end-to-end encryption key on the devices. */
    val e2eSalt: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

object SyncPaths {
    const val API = "/api/v1"
    const val REGISTER = "$API/auth/register"
    const val LOGIN = "$API/auth/login"
    const val ME = "$API/me"
    const val PUSH = "$API/sync/push"
    const val PULL = "$API/sync/pull"
    const val HEALTH = "/health"
}
