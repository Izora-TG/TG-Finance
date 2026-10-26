package com.tgfinance.app.ui

import app.cash.turbine.test
import com.tgfinance.app.ui.budgets.BudgetsViewModel
import com.tgfinance.app.ui.dashboard.DashboardViewModel
import com.tgfinance.app.ui.goals.GoalsViewModel
import com.tgfinance.app.ui.statistics.StatisticsViewModel
import com.tgfinance.app.ui.transactions.PeriodPreset
import com.tgfinance.app.ui.transactions.TransactionsViewModel
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.PeriodType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OtherViewModelsTest : ViewModelTestBase() {

    private val today get() = time.today

    @Before
    fun setUp() {
        seed()
        store.transactions.value = listOf(
            Transaction("t1", TransactionType.INCOME, 300_000, "giro", categoryId = "salary", date = today.withDayOfMonth(1)),
            Transaction("t2", TransactionType.EXPENSE, 12_000, "giro", categoryId = "food", date = today, note = "Wocheneinkauf"),
            Transaction("t3", TransactionType.EXPENSE, 3_000, "cash", categoryId = "food", date = today.minusMonths(1)),
            Transaction("t4", TransactionType.TRANSFER, 10_000, "giro", toAccountId = "cash", date = today),
        )
    }

    @Test
    fun dashboardShowsBalanceMonthTotalsAndBudgetWarnings() = runTest {
        store.budgets.value = listOf(Budget("b1", "food", 10_000))
        val vm = DashboardViewModel(accounts, transactions, budgets, goals, settings, recurring, time, sync)
        vm.uiState.test {
            val state = expectMostRecentItem()
            // 1000 + 50 initial + 3000 income − 150 expenses (transfer neutral)
            assertEquals(390_000L, state.totalBalance)
            assertEquals(300_000L, state.totals!!.income)
            assertEquals(12_000L, state.totals!!.expense)
            assertEquals("Lebensmittel", state.topCategories.single().category.name)
            assertEquals(1, state.overBudget.size)
            assertEquals(4, state.recent.size)
        }
    }

    @Test
    fun dashboardRespectsCustomMonthStart() = runTest {
        settings.setMonthStartDay(20)
        val vm = DashboardViewModel(accounts, transactions, budgets, goals, settings, recurring, time, sync)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(LocalDate.of(2024, 4, 20), state.period!!.start)
            // The income on May 1st and expense on May 15th are both inside Apr 20 – May 19.
            assertEquals(300_000L, state.totals!!.income)
            assertEquals(12_000L, state.totals!!.expense)
        }
    }

    @Test
    fun dashboardRefreshProcessesRecurringBookings() = runTest {
        val vm = DashboardViewModel(accounts, transactions, budgets, goals, settings, recurring, time, sync)
        vm.refresh()
        assertEquals(today, recurring.processedUpTo)
        assertEquals(1, sync.syncCalls)
        assertFalse(vm.refreshing.value)
    }

    @Test
    fun transactionsFilterAndUndoDelete() = runTest {
        val vm = TransactionsViewModel(transactions, accounts, categories, settings, time)
        vm.uiState.test {
            advanceTimeBy(200)
            assertEquals(4, expectMostRecentItem().count)

            vm.setQuery("woche")
            advanceTimeBy(200)
            assertEquals(1, expectMostRecentItem().count)

            vm.clearFilters()
            vm.setPeriod(PeriodPreset.LAST_MONTH)
            advanceTimeBy(200)
            assertEquals(listOf("t3"), expectMostRecentItem().groups.flatMap { g -> g.items.map { it.transaction.id } })

            vm.clearFilters()
            vm.setType(TransactionType.EXPENSE)
            vm.setAccount("giro")
            advanceTimeBy(200)
            val state = expectMostRecentItem()
            assertTrue(state.filter.isActive)
            assertEquals(12_000L, state.expense)
            cancelAndIgnoreRemainingEvents()
        }

        val tx = store.transactions.value.first { it.id == "t2" }
        vm.delete(tx)
        assertEquals(tx, vm.lastDeleted.value)
        assertNull(store.transactions.value.firstOrNull { it.id == "t2" })
        vm.undoDelete()
        assertTrue(store.transactions.value.any { it.id == "t2" })
        assertNull(vm.lastDeleted.value)
    }

    @Test
    fun dayGroupsAreSortedAndSummed() {
        val groups = TransactionsViewModel.group(
            store.transactions.value.map { com.tgfinance.core.model.TransactionDetails(it, null, null, null) },
        )
        assertEquals(today, groups.first().date)
        assertEquals(-12_000L, groups.first().net) // transfer is neutral
    }

    @Test
    fun budgetsListAvailableCategoriesAndNavigatePeriods() = runTest {
        val vm = BudgetsViewModel(budgets, categories, settings, time)
        vm.uiState.test {
            assertEquals(listOf("food"), expectMostRecentItem().availableCategories.map { it.id })
            assertTrue(vm.save(null, "food", "200"))
            val state = expectMostRecentItem()
            assertEquals(20_000L, state.totalBudget)
            assertEquals(12_000L, state.totalSpent)
            assertTrue(state.availableCategories.isEmpty())

            vm.previousPeriod()
            val previous = expectMostRecentItem()
            assertFalse(previous.isCurrentPeriod)
            assertEquals(3_000L, previous.totalSpent)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.save(null, "food", "0"))
    }

    @Test
    fun statisticsSwitchPeriods() = runTest {
        val vm = StatisticsViewModel(transactions, settings, time)
        vm.uiState.test {
            val month = expectMostRecentItem()
            assertEquals(31, month.buckets.size)
            assertTrue(month.bucketsAreDays)
            assertEquals(0.96f, month.savingsRate!!, 0.001f)

            vm.setPeriodType(PeriodType.YEAR)
            val year = expectMostRecentItem()
            assertEquals(12, year.buckets.size)
            assertEquals(15_000L, year.totals!!.expense)

            vm.previous()
            assertEquals(2023, expectMostRecentItem().range!!.start.year)
            vm.next()
            vm.next() // cannot go into the future
            assertEquals(0, expectMostRecentItem().selection.offset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun goalsComputeMonthlyAmountAndAdjust() = runTest {
        store.goals.value = listOf(SavingsGoal("g1", "Urlaub", 120_000, 20_000, today.plusMonths(10), 0, "flight"))
        val vm = GoalsViewModel(goals, time)
        vm.uiState.test {
            assertEquals(10_000L, expectMostRecentItem().goals.single().monthlyNeeded)
            assertTrue(vm.adjust("g1", "50", deposit = true))
            assertEquals(25_000L, expectMostRecentItem().goals.single().goal.saved)
            assertFalse(vm.adjust("g1", "x", deposit = false))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
