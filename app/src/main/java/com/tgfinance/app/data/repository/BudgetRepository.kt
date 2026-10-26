package com.tgfinance.app.data.repository

import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>
    /** Budgets with the amount spent in [period], most used first. */
    fun observeProgress(period: DateRange): Flow<List<BudgetProgress>>
    suspend fun get(id: String): Budget?
    /** One budget per category: saving a second one for the same category replaces it. */
    suspend fun save(budget: Budget): String
    suspend fun delete(id: String)
}

@Singleton
class RoomBudgetRepository @Inject constructor(
    db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : BudgetRepository {

    private val dao = db.budgetDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()

    override fun observeBudgets(): Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeProgress(period: DateRange): Flow<List<BudgetProgress>> = combine(
        dao.observeAll(),
        categoryDao.observeAll(),
        transactionDao.observeCategoryTotals(
            TransactionType.EXPENSE,
            period.start.toEpochDay(),
            period.endInclusive.toEpochDay(),
        ),
    ) { budgets, categories, totals ->
        val categoryMap = categories.associateBy { it.id }
        val spent = totals.associate { it.categoryId to it.total }
        budgets.mapNotNull { budget ->
            val category = categoryMap[budget.categoryId]?.toModel() ?: return@mapNotNull null
            BudgetProgress(budget.toModel(), category, spent[budget.categoryId] ?: 0L)
        }.sortedByDescending { it.ratio }
    }

    override suspend fun get(id: String): Budget? = dao.find(id)?.toModel()

    override suspend fun save(budget: Budget): String {
        require(budget.amount > 0) { "Budget must be positive" }
        val existingForCategory = dao.findByCategory(budget.categoryId)
        val id = when {
            budget.id.isNotBlank() -> budget.id
            existingForCategory != null -> existingForCategory.id
            else -> ids.newId()
        }
        dao.upsert(budget.copy(id = id).toEntity(stamps.create()))
        return id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        val entity = dao.find(id) ?: return
        dao.upsert(entity.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
    }
}
