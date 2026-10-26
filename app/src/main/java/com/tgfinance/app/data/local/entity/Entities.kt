package com.tgfinance.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.model.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Columns every synchronised table has. The entity itself (serialised as JSON) is the
 * sync payload; [dirty] is local bookkeeping and never leaves the device.
 *
 * Dates are stored as epoch days so that the entities serialise without custom serializers.
 * There are no foreign keys on purpose: with sync, records may arrive in any order.
 */
interface SyncEntity {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val deviceId: String
    val dirty: Boolean
}

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val type: AccountType,
    val initialBalance: Long,
    val color: Int,
    val icon: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

@Serializable
@Entity(tableName = "categories", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: Int,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey override val id: String,
    val type: TransactionType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    /** Epoch day of the booking date. */
    val date: Long,
    val note: String = "",
    val recurringId: String? = null,
    val createdAt: Long,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

@Serializable
@Entity(tableName = "budgets", indices = [Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey override val id: String,
    val categoryId: String,
    /** Monthly limit in cents. */
    val amount: Long,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

@Serializable
@Entity(tableName = "recurring_rules")
data class RecurringEntity(
    @PrimaryKey override val id: String,
    val type: TransactionType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: Long,
    val endDate: Long? = null,
    val nextDate: Long,
    val active: Boolean = true,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

@Serializable
@Entity(tableName = "savings_goals")
data class GoalEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val target: Long,
    val saved: Long,
    val targetDate: Long? = null,
    val color: Int,
    val icon: String,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    @Transient override val dirty: Boolean = false,
) : SyncEntity

/** Local log of sync conflicts so that the user can see what was overwritten. */
@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val entityType: String,
    val entityId: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localDeviceId: String,
    val remoteDeviceId: String,
    val winner: String,
    val detectedAt: Long,
)
