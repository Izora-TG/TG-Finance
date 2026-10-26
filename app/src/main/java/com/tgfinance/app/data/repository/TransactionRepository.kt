package com.tgfinance.app.data.repository

import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.TransactionEntity
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.BalancePoint
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.PeriodTotals
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionFilter(
    val range: DateRange? = null,
    val type: TransactionType? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val query: String = "",
) {
    val isActive: Boolean
        get() = range != null || type != null || accountId != null || categoryId != null || query.isNotBlank()
}

interface TransactionRepository {
    fun observe(filter: TransactionFilter): Flow<List<TransactionDetails>>
    fun observeRecent(limit: Int): Flow<List<TransactionDetails>>
    fun observeTotals(range: DateRange): Flow<PeriodTotals>
    fun observeCategoryTotals(range: DateRange, type: TransactionType): Flow<List<CategoryTotal>>
    /** Income/expense sums per bucket (e.g. per day or month) for bar charts. */
    fun observeBucketTotals(buckets: List<DateRange>): Flow<List<PeriodTotals>>
    /** Total balance over all accounts at the end of each bucket. */
    fun observeBalanceTrend(buckets: List<DateRange>): Flow<List<BalancePoint>>
    suspend fun get(id: String): Transaction?
    suspend fun save(transaction: Transaction): String
    suspend fun delete(id: String)
    suspend fun allDetails(): List<TransactionDetails>
}

@Singleton
class RoomTransactionRepository @Inject constructor(
    db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : TransactionRepository {

    private val dao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val accountDao = db.accountDao()

    private val categories: Flow<Map<String, Category>> =
        categoryDao.observeAllIncludingDeleted().map { list -> list.associate { it.id to it.toModel() } }
    private val accounts: Flow<Map<String, Account>> =
        accountDao.observeAll().map { list -> list.associate { it.id to it.toModel() } }

    private fun Flow<List<TransactionEntity>>.withDetails(): Flow<List<TransactionDetails>> =
        combine(this, categories, accounts) { list, categoryMap, accountMap ->
            list.map { entity ->
                val tx = entity.toModel()
                TransactionDetails(
                    transaction = tx,
                    category = tx.categoryId?.let(categoryMap::get),
                    account = accountMap[tx.accountId],
                    toAccount = tx.toAccountId?.let(accountMap::get),
                )
            }
        }

    override fun observe(filter: TransactionFilter): Flow<List<TransactionDetails>> =
        dao.observeFiltered(
            start = filter.range?.start?.toEpochDay(),
            end = filter.range?.endInclusive?.toEpochDay(),
            type = filter.type,
            accountId = filter.accountId,
            categoryId = filter.categoryId,
            query = filter.query.trim().ifEmpty { null },
        ).withDetails()

    override fun observeRecent(limit: Int): Flow<List<TransactionDetails>> =
        dao.observeRecent(limit).withDetails()

    override fun observeTotals(range: DateRange): Flow<PeriodTotals> =
        dao.observeTypeTotals(range.start.toEpochDay(), range.endInclusive.toEpochDay()).map { rows ->
            PeriodTotals(
                start = range.start,
                income = rows.firstOrNull { it.type == TransactionType.INCOME }?.total ?: 0L,
                expense = rows.firstOrNull { it.type == TransactionType.EXPENSE }?.total ?: 0L,
            )
        }

    override fun observeCategoryTotals(range: DateRange, type: TransactionType): Flow<List<CategoryTotal>> =
        combine(
            dao.observeCategoryTotals(type, range.start.toEpochDay(), range.endInclusive.toEpochDay()),
            categories,
        ) { rows, categoryMap ->
            rows.mapNotNull { row ->
                val category = row.categoryId?.let(categoryMap::get) ?: return@mapNotNull null
                CategoryTotal(category, row.total)
            }.sortedByDescending { it.total }
        }

    override fun observeBucketTotals(buckets: List<DateRange>): Flow<List<PeriodTotals>> {
        if (buckets.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val start = buckets.first().start.toEpochDay()
        val end = buckets.last().endInclusive.toEpochDay()
        return dao.observeDailyTotals(start, end).map { rows ->
            buckets.map { bucket ->
                val from = bucket.start.toEpochDay()
                val to = bucket.endInclusive.toEpochDay()
                val inBucket = rows.filter { it.date in from..to }
                PeriodTotals(
                    start = bucket.start,
                    income = inBucket.filter { it.type == TransactionType.INCOME }.sumOf { it.total },
                    expense = inBucket.filter { it.type == TransactionType.EXPENSE }.sumOf { it.total },
                )
            }
        }
    }

    override fun observeBalanceTrend(buckets: List<DateRange>): Flow<List<BalancePoint>> {
        if (buckets.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val start = buckets.first().start
        val initialBalances = accountDao.observeAll().map { list -> list.sumOf { it.initialBalance } }
        return combine(
            initialBalances,
            dao.observeNetBefore(start.toEpochDay()),
            observeBucketTotals(buckets),
        ) { initial, netBefore, totals ->
            var running = initial + netBefore
            buckets.zip(totals) { bucket, total ->
                running += total.net
                BalancePoint(bucket.endInclusive, running)
            }
        }
    }

    override suspend fun get(id: String): Transaction? = dao.find(id)?.toModel()

    override suspend fun save(transaction: Transaction): String {
        require(transaction.amount > 0) { "Amount must be positive" }
        val normalized = when (transaction.type) {
            TransactionType.TRANSFER -> {
                require(transaction.toAccountId != null && transaction.toAccountId != transaction.accountId) {
                    "Transfer needs two different accounts"
                }
                transaction.copy(categoryId = null)
            }
            else -> transaction.copy(toAccountId = null)
        }
        val withId = if (normalized.id.isBlank()) normalized.copy(id = ids.newId()) else normalized
        val existing = dao.findAny(withId.id)
        val stamp = stamps.create()
        val entity = withId.toEntity(stamp).let { if (existing != null) it.copy(createdAt = existing.createdAt) else it }
        dao.upsert(entity)
        return withId.id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        val entity = dao.find(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
    }

    override suspend fun allDetails(): List<TransactionDetails> {
        val categoryMap = categories.first()
        val accountMap = accounts.first()
        return dao.allActive().map { entity ->
            val tx = entity.toModel()
            TransactionDetails(tx, tx.categoryId?.let(categoryMap::get), accountMap[tx.accountId], tx.toAccountId?.let(accountMap::get))
        }
    }
}
