package com.tgfinance.server.sync

import com.tgfinance.core.sync.LastWriteWins
import com.tgfinance.core.sync.PullResponse
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.PushResponse
import com.tgfinance.core.sync.RecordKey
import com.tgfinance.core.sync.SyncRecord
import com.tgfinance.core.sync.version
import com.tgfinance.server.db.Conflicts
import com.tgfinance.server.db.Records
import com.tgfinance.server.db.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

class ValidationException(message: String) : Exception(message)

/**
 * Stores opaque records per user and applies the same last-write-wins rule as the clients.
 * Every accepted write gets the next per-user sequence number, which is the pull cursor.
 */
class SyncService(
    private val db: Database,
    private val maxRecords: Int = 1000,
    private val maxPayloadBytes: Int = 64 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(SyncService::class.java)

    fun push(userId: Long, request: PushRequest): PushResponse {
        validate(request)
        return transaction(db) {
            // Lock the user row so that concurrent pushes of one user are serialised.
            var seq = Users.selectAll().where { Users.id eq userId }.forUpdate().single()[Users.seq]
            val accepted = mutableListOf<RecordKey>()
            val rejected = mutableListOf<SyncRecord>()

            for (incoming in request.records) {
                val existing = Records.selectAll()
                    .where { (Records.userId eq userId) and (Records.type eq incoming.type) and (Records.recordId eq incoming.id) }
                    .singleOrNull()
                    ?.toRecord()
                when {
                    existing == null -> {
                        seq++
                        Records.insert {
                            it[Records.userId] = userId
                            it[type] = incoming.type
                            it[recordId] = incoming.id
                            it[updatedAt] = incoming.updatedAt
                            it[deleted] = incoming.deleted
                            it[deviceId] = incoming.deviceId
                            it[payload] = incoming.payload
                            it[Records.seq] = seq
                        }
                        accepted += incoming.key
                    }
                    LastWriteWins.isSameVersion(existing.version, incoming.version) -> accepted += incoming.key
                    LastWriteWins.candidateWins(incoming.version, existing.version) -> {
                        seq++
                        Records.update({
                            (Records.userId eq userId) and (Records.type eq incoming.type) and (Records.recordId eq incoming.id)
                        }) {
                            it[updatedAt] = incoming.updatedAt
                            it[deleted] = incoming.deleted
                            it[deviceId] = incoming.deviceId
                            it[payload] = incoming.payload
                            it[Records.seq] = seq
                        }
                        accepted += incoming.key
                    }
                    else -> {
                        rejected += existing
                        Conflicts.insert {
                            it[Conflicts.userId] = userId
                            it[type] = incoming.type
                            it[recordId] = incoming.id
                            it[incomingUpdatedAt] = incoming.updatedAt
                            it[incomingDeviceId] = incoming.deviceId
                            it[storedUpdatedAt] = existing.updatedAt
                            it[storedDeviceId] = existing.deviceId
                            it[detectedAt] = clock()
                        }
                        log.info(
                            "Conflict user={} {}:{} incoming={}@{} stored={}@{} -> stored version wins",
                            userId, incoming.type, incoming.id, incoming.updatedAt, incoming.deviceId,
                            existing.updatedAt, existing.deviceId,
                        )
                    }
                }
            }
            Users.update({ Users.id eq userId }) { it[Users.seq] = seq }
            PushResponse(accepted, rejected)
        }
    }

    fun pull(userId: Long, since: Long, limit: Int): PullResponse {
        val pageSize = limit.coerceIn(1, maxRecords)
        return transaction(db) {
            val rows = Records.selectAll()
                .where { (Records.userId eq userId) and (Records.seq greater since) }
                .orderBy(Records.seq, SortOrder.ASC)
                .limit(pageSize + 1)
                .toList()
            val page = rows.take(pageSize)
            PullResponse(
                records = page.map { it.toRecord() },
                cursor = page.lastOrNull()?.get(Records.seq) ?: since,
                hasMore = rows.size > pageSize,
            )
        }
    }

    private fun validate(request: PushRequest) {
        if (request.records.size > maxRecords) throw ValidationException("Too many records (max $maxRecords)")
        request.records.forEach { r ->
            if (r.type !in ALLOWED_TYPES) throw ValidationException("Unknown record type '${r.type}'")
            if (r.id.isBlank() || r.id.length > 128) throw ValidationException("Invalid record id")
            if (r.deviceId.isBlank() || r.deviceId.length > 64) throw ValidationException("Invalid device id")
            if (r.payload.length > maxPayloadBytes) throw ValidationException("Payload too large")
        }
    }

    private fun ResultRow.toRecord() = SyncRecord(
        type = this[Records.type],
        id = this[Records.recordId],
        updatedAt = this[Records.updatedAt],
        deleted = this[Records.deleted],
        deviceId = this[Records.deviceId],
        payload = this[Records.payload],
    )

    companion object {
        val ALLOWED_TYPES = setOf("account", "category", "transaction", "budget", "recurring", "goal")
    }
}
