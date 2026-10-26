package com.tgfinance.app.data.repository

import androidx.room.withTransaction
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.time.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface RecurringRepository {
    fun observeRules(): Flow<List<RecurringRule>>
    suspend fun get(id: String): RecurringRule?
    suspend fun save(rule: RecurringRule): String
    suspend fun delete(id: String)
    /** Creates the bookings of all rules that are due up to [today]; returns how many were created. */
    suspend fun processDue(today: LocalDate): Int
}

@Singleton
class RoomRecurringRepository @Inject constructor(
    private val db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : RecurringRepository {

    private val dao = db.recurringDao()

    override fun observeRules(): Flow<List<RecurringRule>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun get(id: String): RecurringRule? = dao.find(id)?.toModel()

    override suspend fun save(rule: RecurringRule): String {
        require(rule.amount > 0) { "Amount must be positive" }
        val withId = if (rule.id.isBlank()) rule.copy(id = ids.newId()) else rule
        val existing = dao.find(withId.id)?.toModel()
        // Re-anchor the schedule when start or rhythm changed; otherwise keep the progress.
        val scheduleChanged = existing == null ||
            existing.startDate != withId.startDate ||
            existing.frequency != withId.frequency ||
            existing.interval != withId.interval
        val next = if (scheduleChanged) withId.startDate else existing!!.nextDate
        dao.upsert(withId.copy(nextDate = next).toEntity(stamps.create()))
        return withId.id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        val entity = dao.find(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
    }

    override suspend fun processDue(today: LocalDate): Int {
        var created = 0
        db.withTransaction {
            val stamp = stamps.create()
            val txDao = db.transactionDao()
            for (entity in dao.due(today.toEpochDay())) {
                val rule = entity.toModel()
                val dates = Recurrence.dueDates(
                    start = rule.startDate,
                    frequency = rule.frequency,
                    interval = rule.interval,
                    nextDate = rule.nextDate,
                    today = today,
                    endDate = rule.endDate,
                )
                for (date in dates) {
                    // Deterministic id: two devices processing the same rule offline create the
                    // same booking, which the sync then merges instead of duplicating.
                    val id = "rec-${rule.id}-$date"
                    if (txDao.findAny(id) != null) continue
                    val tx = Transaction(
                        id = id,
                        type = rule.type,
                        amount = rule.amount,
                        accountId = rule.accountId,
                        toAccountId = rule.toAccountId,
                        categoryId = rule.categoryId,
                        date = date,
                        note = rule.note,
                        recurringId = rule.id,
                    )
                    txDao.upsert(tx.toEntity(stamp))
                    created++
                }
                val last = dates.lastOrNull() ?: rule.nextDate.minusDays(1)
                val next = Recurrence.nextAfter(rule.startDate, rule.frequency, rule.interval, maxOf(last, today))
                val finished = rule.endDate != null && next.isAfter(rule.endDate)
                dao.upsert(rule.copy(nextDate = next, active = !finished).toEntity(stamp))
            }
        }
        return created
    }
}
