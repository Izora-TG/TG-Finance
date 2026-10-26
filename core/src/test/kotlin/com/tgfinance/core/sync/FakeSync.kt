package com.tgfinance.core.sync

/** In-memory server that implements the same last-write-wins rules as the Ktor backend. */
class FakeSyncServer {
    private data class Stored(val record: SyncRecord, val seq: Long)

    private val records = linkedMapOf<RecordKey, Stored>()
    private var seq = 0L
    val conflicts = mutableListOf<RecordKey>()
    var pullCalls = 0
    var pushCalls = 0

    fun all(): List<SyncRecord> = records.values.map { it.record }
    fun get(key: RecordKey): SyncRecord? = records[key]?.record

    fun seed(record: SyncRecord) {
        records[record.key] = Stored(record, ++seq)
    }

    fun api(): SyncApi = object : SyncApi {
        override suspend fun pull(since: Long, limit: Int): PullResponse {
            pullCalls++
            val changed = records.values.filter { it.seq > since }.sortedBy { it.seq }
            val page = changed.take(limit)
            return PullResponse(
                records = page.map { it.record },
                cursor = page.lastOrNull()?.seq ?: since,
                hasMore = changed.size > limit,
            )
        }

        override suspend fun push(request: PushRequest): PushResponse {
            pushCalls++
            val accepted = mutableListOf<RecordKey>()
            val rejected = mutableListOf<SyncRecord>()
            for (incoming in request.records) {
                val existing = records[incoming.key]?.record
                when {
                    existing == null || LastWriteWins.candidateWins(incoming.version, existing.version) -> {
                        records[incoming.key] = Stored(incoming, ++seq)
                        accepted += incoming.key
                    }
                    LastWriteWins.isSameVersion(existing.version, incoming.version) -> accepted += incoming.key
                    else -> {
                        conflicts += incoming.key
                        rejected += existing
                    }
                }
            }
            return PushResponse(accepted, rejected)
        }
    }
}

class FakeLocalStore(override val deviceId: String) : LocalSyncStore {
    data class Row(val record: SyncRecord, val dirty: Boolean)

    val rows = linkedMapOf<RecordKey, Row>()
    val conflicts = mutableListOf<SyncConflict>()
    private var cursor = 0L

    /** Simulates a local edit made by the user. */
    fun edit(type: String, id: String, payload: String, at: Long, deleted: Boolean = false) {
        val record = SyncRecord(type, id, at, deleted, deviceId, payload)
        rows[record.key] = Row(record, dirty = true)
    }

    fun payload(type: String, id: String): String? = rows[RecordKey(type, id)]?.record?.payload

    override suspend fun cursor(): Long = cursor
    override suspend fun saveCursor(cursor: Long) {
        this.cursor = cursor
    }

    override suspend fun state(key: RecordKey): LocalRecordState? =
        rows[key]?.let { LocalRecordState(it.record.version, it.dirty) }

    override suspend fun dirtyRecords(): List<SyncRecord> = rows.values.filter { it.dirty }.map { it.record }

    override suspend fun applyRemote(record: SyncRecord) {
        rows[record.key] = Row(record, dirty = false)
    }

    override suspend fun markClean(record: SyncRecord) {
        val row = rows[record.key] ?: return
        if (LastWriteWins.isSameVersion(row.record.version, record.version)) {
            rows[record.key] = row.copy(dirty = false)
        }
    }

    override suspend fun logConflict(conflict: SyncConflict) {
        conflicts += conflict
    }
}
