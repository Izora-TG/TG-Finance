package com.tgfinance.app.ui.preview

import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.AccountWithBalance
import com.tgfinance.core.model.BalancePoint
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.model.PeriodTotals
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import java.time.LocalDate

/** Sample data for @Preview functions. */
object PreviewData {
    private val today: LocalDate = LocalDate.of(2024, 5, 15)

    val checking = Account("a1", "Girokonto", AccountType.CHECKING, 150_000, 0xFF8B5CF6.toInt(), "bank")
    val cash = Account("a2", "Bargeld", AccountType.CASH, 8_000, 0xFF22C55E.toInt(), "cash")
    val savings = Account("a3", "Sparkonto", AccountType.SAVINGS, 500_000, 0xFF0EA5E9.toInt(), "savings")

    val accounts = listOf(
        AccountWithBalance(checking, 243_512),
        AccountWithBalance(cash, 6_450),
        AccountWithBalance(savings, 520_000),
    )

    val groceries = Category("c1", "Lebensmittel", CategoryType.EXPENSE, "cart", 0xFF22C55E.toInt())
    val housing = Category("c2", "Wohnen & Miete", CategoryType.EXPENSE, "home", 0xFF8B5CF6.toInt())
    val dining = Category("c3", "Restaurant & Café", CategoryType.EXPENSE, "restaurant", 0xFFF97316.toInt())
    val subscriptions = Category("c4", "Abos & Medien", CategoryType.EXPENSE, "subscriptions", 0xFF6366F1.toInt())
    val salary = Category("c5", "Gehalt", CategoryType.INCOME, "work", 0xFF4ADE80.toInt())

    val categories = listOf(groceries, housing, dining, subscriptions, salary)

    val transactions = listOf(
        TransactionDetails(
            Transaction("t1", TransactionType.EXPENSE, 4_390, "a1", categoryId = "c1", date = today, note = "Wocheneinkauf"),
            groceries, checking, null,
        ),
        TransactionDetails(
            Transaction("t2", TransactionType.INCOME, 285_000, "a1", categoryId = "c5", date = today.minusDays(1)),
            salary, checking, null,
        ),
        TransactionDetails(
            Transaction("t3", TransactionType.EXPENSE, 95_000, "a1", categoryId = "c2", date = today.minusDays(1), recurringId = "r1"),
            housing, checking, null,
        ),
        TransactionDetails(
            Transaction("t4", TransactionType.TRANSFER, 20_000, "a1", toAccountId = "a3", date = today.minusDays(3)),
            null, checking, savings,
        ),
        TransactionDetails(
            Transaction("t5", TransactionType.EXPENSE, 1_299, "a2", categoryId = "c3", date = today.minusDays(4), note = "Mittagessen"),
            dining, cash, null,
        ),
    )

    val budgets = listOf(
        BudgetProgress(Budget("b1", "c1", 40_000), groceries, 36_500),
        BudgetProgress(Budget("b2", "c3", 15_000), dining, 17_250),
        BudgetProgress(Budget("b3", "c4", 5_000), subscriptions, 2_197),
    )

    val categoryTotals = listOf(
        CategoryTotal(housing, 95_000),
        CategoryTotal(groceries, 36_500),
        CategoryTotal(dining, 17_250),
        CategoryTotal(subscriptions, 2_197),
    )

    val monthTotals = PeriodTotals(today.withDayOfMonth(1), income = 285_000, expense = 150_947)

    val barTotals = (5 downTo 0).map {
        PeriodTotals(today.minusMonths(it.toLong()).withDayOfMonth(1), 270_000L + it * 5_000, 150_000L + (it % 3) * 30_000)
    }

    val balanceTrend = (0 until 30).map {
        BalancePoint(today.minusDays(29L - it), 700_000L + it * 2_000 - (it % 7) * 9_000)
    }

    val goals = listOf(
        SavingsGoal("g1", "Urlaub Japan", 300_000, 185_000, today.plusMonths(8), 0xFF0EA5E9.toInt(), "flight"),
        SavingsGoal("g2", "Notgroschen", 600_000, 600_000, null, 0xFF8B5CF6.toInt(), "shield"),
    )

    val recurring = listOf(
        RecurringRule("r1", TransactionType.EXPENSE, 95_000, "a1", categoryId = "c2", note = "Miete", frequency = Frequency.MONTHLY, startDate = today.withDayOfMonth(1), nextDate = today.plusMonths(1).withDayOfMonth(1)),
        RecurringRule("r2", TransactionType.EXPENSE, 1_299, "a1", categoryId = "c4", note = "Streaming", frequency = Frequency.MONTHLY, startDate = today.minusMonths(3), nextDate = today.plusDays(5)),
        RecurringRule("r3", TransactionType.INCOME, 285_000, "a1", categoryId = "c5", note = "Gehalt", frequency = Frequency.MONTHLY, startDate = today.minusMonths(6), nextDate = today.plusDays(14)),
    )
}
