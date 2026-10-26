package com.tgfinance.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomBudgetRepository
import com.tgfinance.app.data.repository.RoomCategoryRepository
import com.tgfinance.app.data.repository.RoomGoalRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.PeriodCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccountAndBudgetRepositoryTest : DatabaseTest() {

    private val accounts by lazy { RoomAccountRepository(db, stamps, ids) }
    private val categories by lazy { RoomCategoryRepository(db, stamps, ids) }
    private val transactions by lazy { RoomTransactionRepository(db, stamps, ids) }
    private val budgets by lazy { RoomBudgetRepository(db, stamps, ids) }
    private val goals by lazy { RoomGoalRepository(db, stamps, ids) }
    private val day = LocalDate.of(2024, 5, 10)

    @Test
    fun balancesIncludeInitialIncomeExpenseAndTransfers() = runTest {
        val giro = accounts.save(account("Giro", initial = 100_000))
        val savings = accounts.save(account("Spar", initial = 50_000))
        transactions.save(Transaction("", TransactionType.INCOME, 20_000, giro, date = day))
        transactions.save(Transaction("", TransactionType.EXPENSE, 5_000, giro, date = day))
        transactions.save(Transaction("", TransactionType.TRANSFER, 30_000, giro, toAccountId = savings, date = day))

        val balances = accounts.observeAccounts().first().associate { it.account.name to it.balance }
        assertEquals(85_000L, balances["Giro"])
        assertEquals(80_000L, balances["Spar"])
        // Transfers do not change the total.
        assertEquals(165_000L, accounts.observeTotalBalance().first())
    }

    @Test
    fun deletingAccountSoftDeletesItsTransactions() = runTest {
        val giro = accounts.save(account("Giro"))
        val other = accounts.save(account("Bar"))
        val tx = transactions.save(Transaction("", TransactionType.EXPENSE, 100, giro, date = day))
        val keep = transactions.save(Transaction("", TransactionType.EXPENSE, 100, other, date = day))

        accounts.delete(giro)

        assertNull(accounts.get(giro))
        assertTrue(db.accountDao().findAny(giro)!!.deleted)
        assertTrue(db.transactionDao().findAny(tx)!!.deleted)
        assertTrue(!db.transactionDao().findAny(keep)!!.deleted)
    }

    @Test
    fun budgetProgressCountsOnlyPeriodExpensesOfCategory() = runTest {
        val giro = accounts.save(account("Giro"))
        val food = categories.save(category("Lebensmittel"))
        val other = categories.save(category("Sonstiges"))
        budgets.save(Budget("", food, 10_000))
        transactions.save(Transaction("", TransactionType.EXPENSE, 8_500, giro, categoryId = food, date = day))
        transactions.save(Transaction("", TransactionType.EXPENSE, 9_999, giro, categoryId = other, date = day))
        transactions.save(Transaction("", TransactionType.EXPENSE, 9_999, giro, categoryId = food, date = day.minusMonths(1)))

        val progress = budgets.observeProgress(PeriodCalculator.month(day)).first().single()
        assertEquals(8_500L, progress.spent)
        assertTrue(progress.isWarning)
        assertTrue(!progress.isOver)
    }

    @Test
    fun secondBudgetForCategoryReplacesFirst() = runTest {
        val food = categories.save(category("Lebensmittel"))
        val first = budgets.save(Budget("", food, 10_000))
        val second = budgets.save(Budget("", food, 20_000))
        assertEquals(first, second)
        assertEquals(20_000L, budgets.observeBudgets().first().single().amount)
    }

    @Test
    fun deletingCategoryRemovesItsBudget() = runTest {
        val food = categories.save(category("Lebensmittel"))
        budgets.save(Budget("", food, 10_000))
        categories.delete(food)
        assertTrue(budgets.observeBudgets().first().isEmpty())
        assertTrue(categories.observeCategories(CategoryType.EXPENSE).first().isEmpty())
        // Deleted categories remain available for labelling old bookings.
        assertEquals(1, categories.observeAllIncludingDeleted().first().size)
    }

    @Test
    fun categoriesSortedByUsage() = runTest {
        val giro = accounts.save(account("Giro"))
        val a = categories.save(category("A"))
        val b = categories.save(category("B"))
        repeat(3) { transactions.save(Transaction("", TransactionType.EXPENSE, 100, giro, categoryId = b, date = day)) }
        val sorted = categories.observeByUsage(CategoryType.EXPENSE, day.minusDays(90)).first()
        assertEquals(listOf(b, a), sorted.map { it.id })
    }

    @Test
    fun goalAdjustNeverGoesBelowZero() = runTest {
        val id = goals.save(SavingsGoal("", "Urlaub", 100_000, 10_000, null, 0, "flight"))
        goals.adjust(id, 5_000)
        assertEquals(15_000L, goals.get(id)!!.saved)
        goals.adjust(id, -50_000)
        assertEquals(0L, goals.get(id)!!.saved)
    }
}
