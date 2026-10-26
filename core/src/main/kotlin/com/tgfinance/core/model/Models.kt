package com.tgfinance.core.model

import java.time.LocalDate

/** Amounts are always stored as minor units (cents) in a [Long]. */
typealias Cents = Long

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

enum class CategoryType { INCOME, EXPENSE }

enum class AccountType { CHECKING, CASH, SAVINGS, CREDIT_CARD, INVESTMENT, OTHER }

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val initialBalance: Cents,
    val color: Int,
    val icon: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

data class AccountWithBalance(
    val account: Account,
    val balance: Cents,
)

data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: Int,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Cents,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate,
    val note: String = "",
    val recurringId: String? = null,
    val createdAt: Long = 0L,
) {
    /** Effect of this booking on the overall balance (transfers are neutral). */
    val signedAmount: Cents
        get() = when (type) {
            TransactionType.INCOME -> amount
            TransactionType.EXPENSE -> -amount
            TransactionType.TRANSFER -> 0L
        }
}

data class TransactionDetails(
    val transaction: Transaction,
    val category: Category?,
    val account: Account?,
    val toAccount: Account?,
)

data class Budget(
    val id: String,
    val categoryId: String,
    val amount: Cents,
)

data class BudgetProgress(
    val budget: Budget,
    val category: Category,
    val spent: Cents,
) {
    val ratio: Float
        get() = if (budget.amount <= 0) 0f else spent.toFloat() / budget.amount.toFloat()
    val remaining: Cents get() = budget.amount - spent
    val isOver: Boolean get() = spent > budget.amount
    val isWarning: Boolean get() = !isOver && ratio >= WARNING_THRESHOLD

    companion object {
        const val WARNING_THRESHOLD = 0.8f
    }
}

data class RecurringRule(
    val id: String,
    val type: TransactionType,
    val amount: Cents,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextDate: LocalDate = startDate,
    val active: Boolean = true,
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val target: Cents,
    val saved: Cents,
    val targetDate: LocalDate? = null,
    val color: Int,
    val icon: String,
) {
    val progress: Float
        get() = if (target <= 0) 0f else (saved.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val isReached: Boolean get() = target in 1..saved
}

data class CategoryTotal(
    val category: Category,
    val total: Cents,
)

/** Income/expense sums for one bucket (e.g. a month) of a statistics chart. */
data class PeriodTotals(
    val start: LocalDate,
    val income: Cents,
    val expense: Cents,
) {
    val net: Cents get() = income - expense
}

/** Balance at the end of [date]; used for the balance trend chart. */
data class BalancePoint(
    val date: LocalDate,
    val balance: Cents,
)
