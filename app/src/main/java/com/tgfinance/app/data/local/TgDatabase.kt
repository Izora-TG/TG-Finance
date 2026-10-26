package com.tgfinance.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tgfinance.app.data.local.dao.AccountDao
import com.tgfinance.app.data.local.dao.BudgetDao
import com.tgfinance.app.data.local.dao.CategoryDao
import com.tgfinance.app.data.local.dao.GoalDao
import com.tgfinance.app.data.local.dao.RecurringDao
import com.tgfinance.app.data.local.dao.SyncConflictDao
import com.tgfinance.app.data.local.dao.TransactionDao
import com.tgfinance.app.data.local.entity.AccountEntity
import com.tgfinance.app.data.local.entity.BudgetEntity
import com.tgfinance.app.data.local.entity.CategoryEntity
import com.tgfinance.app.data.local.entity.GoalEntity
import com.tgfinance.app.data.local.entity.RecurringEntity
import com.tgfinance.app.data.local.entity.SyncConflictEntity
import com.tgfinance.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        GoalEntity::class,
        SyncConflictEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TgDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun goalDao(): GoalDao
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {
        const val NAME = "tg_finance.db"
    }
}
