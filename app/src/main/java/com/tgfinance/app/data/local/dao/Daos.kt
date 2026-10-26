package com.tgfinance.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.tgfinance.app.data.local.entity.AccountEntity
import com.tgfinance.app.data.local.entity.BudgetEntity
import com.tgfinance.app.data.local.entity.CategoryEntity
import com.tgfinance.app.data.local.entity.GoalEntity
import com.tgfinance.app.data.local.entity.RecurringEntity
import com.tgfinance.app.data.local.entity.SyncConflictEntity
import com.tgfinance.app.data.local.entity.TransactionEntity
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Operations the sync layer needs on every synchronised table. */
interface SyncDao<E> {
    suspend fun findAny(id: String): E?
    suspend fun dirty(): List<E>
    suspend fun upsert(entity: E)
    suspend fun upsertAll(entities: List<E>)
    suspend fun markClean(id: String, updatedAt: Long, deviceId: String)
    suspend fun allIncludingDeleted(): List<E>
}

data class AccountBalanceRow(
    @Embedded val account: AccountEntity,
    val balance: Long,
)

@Dao
interface AccountDao : SyncDao<AccountEntity> {
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT a.*, a.initialBalance
            + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                        FROM transactions t WHERE t.accountId = a.id AND t.deleted = 0), 0)
            + COALESCE((SELECT SUM(t.amount) FROM transactions t
                        WHERE t.toAccountId = a.id AND t.type = 'TRANSFER' AND t.deleted = 0), 0)
            AS balance
        FROM accounts a
        WHERE a.deleted = 0
        ORDER BY a.archived, a.sortOrder, a.name COLLATE NOCASE
        """,
    )
    fun observeWithBalances(): Flow<List<AccountBalanceRow>>

    @Query("SELECT * FROM accounts WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE deleted = 0")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE id = :id")
    override suspend fun findAny(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE dirty = 1")
    override suspend fun dirty(): List<AccountEntity>

    @Upsert
    override suspend fun upsert(entity: AccountEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<AccountEntity>)

    @Query("UPDATE accounts SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM accounts")
    override suspend fun allIncludingDeleted(): List<AccountEntity>
}

data class CategoryUsageRow(
    val categoryId: String,
    val uses: Int,
)

@Dao
interface CategoryDao : SyncDao<CategoryEntity> {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** Includes deleted categories so that old bookings keep showing their category name. */
    @Query("SELECT * FROM categories")
    fun observeAllIncludingDeleted(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 AND type = :type ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS uses FROM transactions
        WHERE deleted = 0 AND categoryId IS NOT NULL AND date >= :sinceEpochDay
        GROUP BY categoryId
        """,
    )
    fun observeUsage(sinceEpochDay: Long): Flow<List<CategoryUsageRow>>

    @Query("SELECT * FROM categories WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE id = :id")
    override suspend fun findAny(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE dirty = 1")
    override suspend fun dirty(): List<CategoryEntity>

    @Upsert
    override suspend fun upsert(entity: CategoryEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<CategoryEntity>)

    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM categories")
    override suspend fun allIncludingDeleted(): List<CategoryEntity>
}

data class TypeTotalRow(
    val type: TransactionType,
    val total: Long,
)

data class CategoryTotalRow(
    val categoryId: String?,
    val total: Long,
)

data class DayTotalRow(
    val date: Long,
    val type: TransactionType,
    val total: Long,
)

@Dao
interface TransactionDao : SyncDao<TransactionEntity> {
    /**
     * All filters are optional (null = no filter). [query] matches the note, the category name
     * and the account name.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted = 0
          AND (:start IS NULL OR date >= :start)
          AND (:end IS NULL OR date <= :end)
          AND (:type IS NULL OR type = :type)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:query IS NULL
               OR note LIKE '%' || :query || '%'
               OR categoryId IN (SELECT id FROM categories WHERE name LIKE '%' || :query || '%')
               OR accountId IN (SELECT id FROM accounts WHERE name LIKE '%' || :query || '%'))
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeFiltered(
        start: Long?,
        end: Long?,
        type: TransactionType?,
        accountId: String?,
        categoryId: String?,
        query: String?,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    suspend fun allActive(): List<TransactionEntity>

    @Query(
        """
        SELECT type, SUM(amount) AS total FROM transactions
        WHERE deleted = 0 AND date BETWEEN :start AND :end
        GROUP BY type
        """,
    )
    fun observeTypeTotals(start: Long, end: Long): Flow<List<TypeTotalRow>>

    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE deleted = 0 AND type = :type AND date BETWEEN :start AND :end
        GROUP BY categoryId
        ORDER BY total DESC
        """,
    )
    fun observeCategoryTotals(type: TransactionType, start: Long, end: Long): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT date, type, SUM(amount) AS total FROM transactions
        WHERE deleted = 0 AND type != 'TRANSFER' AND date BETWEEN :start AND :end
        GROUP BY date, type
        ORDER BY date
        """,
    )
    fun observeDailyTotals(start: Long, end: Long): Flow<List<DayTotalRow>>

    /** Net effect of all income/expense bookings before [date] (transfers are neutral overall). */
    @Query(
        """
        SELECT COALESCE(SUM(CASE type WHEN 'INCOME' THEN amount WHEN 'EXPENSE' THEN -amount ELSE 0 END), 0)
        FROM transactions WHERE deleted = 0 AND date < :date
        """,
    )
    fun observeNetBefore(date: Long): Flow<Long>

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    override suspend fun findAny(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    override suspend fun dirty(): List<TransactionEntity>

    @Upsert
    override suspend fun upsert(entity: TransactionEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<TransactionEntity>)

    @Query("UPDATE transactions SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM transactions")
    override suspend fun allIncludingDeleted(): List<TransactionEntity>
}

@Dao
interface BudgetDao : SyncDao<BudgetEntity> {
    @Query("SELECT * FROM budgets WHERE deleted = 0")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND deleted = 0 LIMIT 1")
    suspend fun findByCategory(categoryId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    override suspend fun findAny(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    override suspend fun dirty(): List<BudgetEntity>

    @Upsert
    override suspend fun upsert(entity: BudgetEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<BudgetEntity>)

    @Query("UPDATE budgets SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM budgets")
    override suspend fun allIncludingDeleted(): List<BudgetEntity>
}

@Dao
interface RecurringDao : SyncDao<RecurringEntity> {
    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 ORDER BY active DESC, nextDate")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 AND active = 1 AND nextDate <= :today")
    suspend fun due(today: Long): List<RecurringEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): RecurringEntity?

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    override suspend fun findAny(id: String): RecurringEntity?

    @Query("SELECT * FROM recurring_rules WHERE dirty = 1")
    override suspend fun dirty(): List<RecurringEntity>

    @Upsert
    override suspend fun upsert(entity: RecurringEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<RecurringEntity>)

    @Query("UPDATE recurring_rules SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM recurring_rules")
    override suspend fun allIncludingDeleted(): List<RecurringEntity>
}

@Dao
interface GoalDao : SyncDao<GoalEntity> {
    @Query("SELECT * FROM savings_goals WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id AND deleted = 0")
    suspend fun find(id: String): GoalEntity?

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    override suspend fun findAny(id: String): GoalEntity?

    @Query("SELECT * FROM savings_goals WHERE dirty = 1")
    override suspend fun dirty(): List<GoalEntity>

    @Upsert
    override suspend fun upsert(entity: GoalEntity)

    @Upsert
    override suspend fun upsertAll(entities: List<GoalEntity>)

    @Query("UPDATE savings_goals SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    override suspend fun markClean(id: String, updatedAt: Long, deviceId: String)

    @Query("SELECT * FROM savings_goals")
    override suspend fun allIncludingDeleted(): List<GoalEntity>
}

@Dao
interface SyncConflictDao {
    @Insert
    suspend fun insert(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SyncConflictEntity>>

    @Query("DELETE FROM sync_conflicts")
    suspend fun clear()
}
