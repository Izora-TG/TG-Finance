package com.tgfinance.core.sync

/** Version information of one record used for conflict resolution. */
data class RecordVersion(
    val updatedAt: Long,
    val deviceId: String,
)

/**
 * Last-write-wins per record. Ties (same timestamp) are broken deterministically by the
 * device id, so that every participant – client and server – picks the same winner.
 */
object LastWriteWins {
    fun candidateWins(candidate: RecordVersion, current: RecordVersion): Boolean =
        candidate.updatedAt > current.updatedAt ||
            (candidate.updatedAt == current.updatedAt && candidate.deviceId > current.deviceId)

    fun isSameVersion(a: RecordVersion, b: RecordVersion): Boolean =
        a.updatedAt == b.updatedAt && a.deviceId == b.deviceId
}

val SyncRecord.version: RecordVersion get() = RecordVersion(updatedAt, deviceId)
