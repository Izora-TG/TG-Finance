package com.tgfinance.app.data.sync

import android.util.Log
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.dao.SyncDao
import com.tgfinance.app.data.local.entity.AccountEntity
import com.tgfinance.app.data.local.entity.BudgetEntity
import com.tgfinance.app.data.local.entity.CategoryEntity
import com.tgfinance.app.data.local.entity.GoalEntity
import com.tgfinance.app.data.local.entity.RecurringEntity
import com.tgfinance.app.data.local.entity.SyncConflictEntity
import com.tgfinance.app.data.local.entity.SyncEntity
import com.tgfinance.app.data.local.entity.TransactionEntity
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.core.sync.LocalRecordState
import com.tgfinance.core.sync.LocalSyncStore
import com.tgfinance.core.sync.RecordKey
import com.tgfinance.core.sync.RecordVersion
import com.tgfinance.core.sync.SyncConflict
import com.tgfinance.core.sync.SyncRecord
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Connects the platform-independent [com.tgfinance.core.sync.SyncEngine] with Room. */
class RoomSyncStore(
    private val db: TgDatabase,
    private val settings: SettingsRepository,
    override val deviceId: String,
) : LocalSyncStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private class Adapter<E : SyncEntity>(
        val type: String,
        val dao: SyncDao<E>,
        val serializer: KSerializer<E>,
        /** Applies the record metadata to a decoded entity and marks it clean. */
        val withMeta: (E, SyncRecord) -> E,
    )

    private val adapters: Map<String, Adapter<*>> = listOf(
        Adapter(TYPE_ACCOUNT, db.accountDao(), AccountEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
        Adapter(TYPE_CATEGORY, db.categoryDao(), CategoryEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
        Adapter(TYPE_TRANSACTION, db.transactionDao(), TransactionEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
        Adapter(TYPE_BUDGET, db.budgetDao(), BudgetEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
        Adapter(TYPE_RECURRING, db.recurringDao(), RecurringEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
        Adapter(TYPE_GOAL, db.goalDao(), GoalEntity.serializer()) { e, r ->
            e.copy(id = r.id, updatedAt = r.updatedAt, deleted = r.deleted, deviceId = r.deviceId, dirty = false)
        },
    ).associateBy { it.type }

    override suspend fun cursor(): Long = settings.syncSettings.first().cursor

    override suspend fun saveCursor(cursor: Long) = settings.updateSync { it.copy(cursor = cursor) }

    override suspend fun state(key: RecordKey): LocalRecordState? {
        val entity = adapters[key.type]?.dao?.findAny(key.id) ?: return null
        return LocalRecordState(RecordVersion(entity.updatedAt, entity.deviceId), entity.dirty)
    }

    override suspend fun dirtyRecords(): List<SyncRecord> = adapters.values.flatMap { it.dirtyRecords() }

    private suspend fun <E : SyncEntity> Adapter<E>.dirtyRecords(): List<SyncRecord> = dao.dirty().map { entity ->
        SyncRecord(type, entity.id, entity.updatedAt, entity.deleted, entity.deviceId, json.encodeToString(serializer, entity))
    }

    override suspend fun applyRemote(record: SyncRecord) {
        val adapter = adapters[record.type] ?: return // unknown type from a newer app version
        adapter.apply(record)
    }

    private suspend fun <E : SyncEntity> Adapter<E>.apply(record: SyncRecord) {
        val decoded = try {
            json.decodeFromString(serializer, record.payload)
        } catch (e: SerializationException) {
            Log.w(TAG, "Skipping undecodable ${record.type} ${record.id}", e)
            return
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping invalid ${record.type} ${record.id}", e)
            return
        }
        dao.upsert(withMeta(decoded, record))
    }

    override suspend fun markClean(record: SyncRecord) {
        adapters[record.type]?.dao?.markClean(record.id, record.updatedAt, record.deviceId)
    }

    override suspend fun logConflict(conflict: SyncConflict) {
        db.syncConflictDao().insert(
            SyncConflictEntity(
                entityType = conflict.key.type,
                entityId = conflict.key.id,
                localUpdatedAt = conflict.local.updatedAt,
                remoteUpdatedAt = conflict.remote.updatedAt,
                localDeviceId = conflict.local.deviceId,
                remoteDeviceId = conflict.remote.deviceId,
                winner = conflict.winner.name,
                detectedAt = conflict.detectedAt,
            ),
        )
    }

    companion object {
        private const val TAG = "RoomSyncStore"
        const val TYPE_ACCOUNT = "account"
        const val TYPE_CATEGORY = "category"
        const val TYPE_TRANSACTION = "transaction"
        const val TYPE_BUDGET = "budget"
        const val TYPE_RECURRING = "recurring"
        const val TYPE_GOAL = "goal"
    }
}
