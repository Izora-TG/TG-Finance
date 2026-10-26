package com.tgfinance.core.sync

/** Remote side of the sync, implemented with HTTP on Android and with fakes in tests. */
interface SyncApi {
    suspend fun pull(since: Long, limit: Int): PullResponse
    suspend fun push(request: PushRequest): PushResponse
}

/** Local state of a record as seen by the sync engine. */
data class LocalRecordState(
    val version: RecordVersion,
    /** True if the record was changed locally and not yet pushed. */
    val dirty: Boolean,
)

/** Local database side of the sync (Room on Android). */
interface LocalSyncStore {
    val deviceId: String
    suspend fun cursor(): Long
    suspend fun saveCursor(cursor: Long)
    suspend fun state(key: RecordKey): LocalRecordState?
    suspend fun dirtyRecords(): List<SyncRecord>
    /** Writes a remote record and marks it clean. */
    suspend fun applyRemote(record: SyncRecord)
    /** Marks a pushed record clean, but only if it was not changed again in the meantime. */
    suspend fun markClean(record: SyncRecord)
    suspend fun logConflict(conflict: SyncConflict)
}

enum class ConflictWinner { LOCAL, REMOTE }

data class SyncConflict(
    val key: RecordKey,
    val local: RecordVersion,
    val remote: RecordVersion,
    val winner: ConflictWinner,
    val detectedAt: Long,
)

data class SyncReport(
    val pulled: Int,
    val pushed: Int,
    val conflicts: List<SyncConflict>,
)

class SyncException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { NETWORK, UNAUTHORIZED, SERVER, ENCRYPTION, CONFIGURATION }
}

/**
 * Offline-first synchronisation: pull everything changed since the last cursor, resolve
 * conflicts per record with [LastWriteWins], then push all locally changed records.
 */
class SyncEngine(
    private val api: SyncApi,
    private val store: LocalSyncStore,
    private val cipher: PayloadCipher? = null,
    private val pageSize: Int = 500,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun sync(): SyncReport {
        val conflicts = mutableListOf<SyncConflict>()
        val pulled = pull(conflicts)
        val pushed = push(conflicts)
        conflicts.forEach { store.logConflict(it) }
        return SyncReport(pulled, pushed, conflicts)
    }

    private suspend fun pull(conflicts: MutableList<SyncConflict>): Int {
        var applied = 0
        var cursor = store.cursor()
        do {
            val page = api.pull(cursor, pageSize)
            for (encoded in page.records) {
                val remote = decode(encoded)
                if (handleIncoming(remote, conflicts)) applied++
            }
            cursor = page.cursor
            store.saveCursor(cursor)
        } while (page.hasMore && page.records.isNotEmpty())
        return applied
    }

    /** Returns true if the remote record was written locally. */
    private suspend fun handleIncoming(remote: SyncRecord, conflicts: MutableList<SyncConflict>): Boolean {
        val local = store.state(remote.key)
        if (local == null) {
            store.applyRemote(remote)
            return true
        }
        if (LastWriteWins.isSameVersion(local.version, remote.version)) {
            return false // echo of our own push or already applied
        }
        val remoteWins = LastWriteWins.candidateWins(remote.version, local.version)
        // An older version written by this device is just the echo of an earlier push that
        // was edited again since – not a concurrent change, so not a conflict.
        val ownEcho = remote.deviceId == store.deviceId && !remoteWins
        if (local.dirty && !ownEcho) {
            conflicts += SyncConflict(
                key = remote.key,
                local = local.version,
                remote = remote.version,
                winner = if (remoteWins) ConflictWinner.REMOTE else ConflictWinner.LOCAL,
                detectedAt = clock(),
            )
        }
        if (remoteWins) {
            store.applyRemote(remote)
            return true
        }
        return false
    }

    private suspend fun push(conflicts: MutableList<SyncConflict>): Int {
        val dirty = store.dirtyRecords()
        var pushed = 0
        for (chunk in dirty.chunked(pageSize)) {
            val response = api.push(PushRequest(store.deviceId, chunk.map(::encode)))
            val accepted = response.accepted.toSet()
            chunk.filter { it.key in accepted }.forEach {
                store.markClean(it)
                pushed++
            }
            val pushedByKey = chunk.associateBy { it.key }
            for (encoded in response.rejected) {
                val serverVersion = decode(encoded)
                val ours = pushedByKey[serverVersion.key]
                if (ours != null) {
                    conflicts += SyncConflict(
                        key = serverVersion.key,
                        local = ours.version,
                        remote = serverVersion.version,
                        winner = ConflictWinner.REMOTE,
                        detectedAt = clock(),
                    )
                }
                // Only overwrite if the record was not edited again while the push was running.
                val current = store.state(serverVersion.key)
                if (current == null || LastWriteWins.candidateWins(serverVersion.version, current.version)) {
                    store.applyRemote(serverVersion)
                }
            }
        }
        return pushed
    }

    private fun encode(record: SyncRecord): SyncRecord =
        if (cipher == null) record else record.copy(payload = cipher.encrypt(record.payload))

    private fun decode(record: SyncRecord): SyncRecord {
        val encrypted = PayloadCipher.isEncrypted(record.payload)
        return when {
            encrypted && cipher == null -> throw SyncException(
                SyncException.Kind.ENCRYPTION,
                "Server data is end-to-end encrypted, but no encryption password is set",
            )
            encrypted -> record.copy(payload = cipher!!.decrypt(record.payload))
            else -> record
        }
    }
}
