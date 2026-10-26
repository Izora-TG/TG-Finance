package com.tgfinance.app.ui

import com.tgfinance.app.fakes.FakeAccountRepository
import com.tgfinance.app.fakes.FakeBudgetRepository
import com.tgfinance.app.fakes.FakeCategoryRepository
import com.tgfinance.app.fakes.FakeGoalRepository
import com.tgfinance.app.fakes.FakeRecurringRepository
import com.tgfinance.app.fakes.FakeSettingsRepository
import com.tgfinance.app.fakes.FakeStore
import com.tgfinance.app.fakes.FakeTimeProvider
import com.tgfinance.app.fakes.FakeTransactionRepository
import com.tgfinance.app.fakes.MainDispatcherRule
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import org.junit.Rule

abstract class ViewModelTestBase {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected val store = FakeStore()
    protected val time = FakeTimeProvider()
    protected val settings = FakeSettingsRepository()
    protected val accounts = FakeAccountRepository(store)
    protected val categories = FakeCategoryRepository(store)
    protected val transactions = FakeTransactionRepository(store)
    protected val budgets = FakeBudgetRepository(store)
    protected val recurring = FakeRecurringRepository(store)
    protected val goals = FakeGoalRepository(store)

    protected val giro = Account("giro", "Girokonto", AccountType.CHECKING, 100_000, 0, "bank")
    protected val cash = Account("cash", "Bargeld", AccountType.CASH, 5_000, 0, "cash")
    protected val food = Category("food", "Lebensmittel", CategoryType.EXPENSE, "cart", 0)
    protected val salary = Category("salary", "Gehalt", CategoryType.INCOME, "work", 0)

    protected fun seed() {
        store.accounts.value = listOf(giro, cash)
        store.categories.value = listOf(food, salary)
    }
}
