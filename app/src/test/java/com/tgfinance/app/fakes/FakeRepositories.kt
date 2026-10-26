package com.tgfinance.app.fakes

import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.BudgetRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.repository.GoalRepository
import com.tgfinance.app.data.repository.RecurringRepository
import com.tgfinance.app.data.repository.TransactionFilter
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountWithBalance
import com.tgfinance.core.model.BalancePoint
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.PeriodTotals
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** In-memory store shared by the fake repositories, so balances and totals stay consistent. */
class FakeStore {
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val budgets = MutableStateFlow<List<Budget>>(emptyList())
    val rules = MutableStateFlow<List<RecurringRule>>(emptyList())
    val goals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    private var nextId = 1
    fun id(prefix: String) = "$prefix-${nextId++}"
}

class FakeAccountRepository(private val store: FakeStore) : AccountRepository {
    override fun observeAccounts(): Flow<List<AccountWithBalance>> =
        combine(store.accounts, store.transactions) { accounts, txs ->
            accounts.map { account ->
                val delta = txs.sumOf { tx ->
                    when {
                        tx.accountId == account.id && tx.type == TransactionType.INCOME -> tx.amount
                        tx.accountId == account.id -> -tx.amount
                        tx.toAccountId == account.id && tx.type == TransactionType.TRANSFER -> tx.amount
                        else -> 0L
                    }
                }
                AccountWithBalance(account, account.initialBalance + delta)
            }
        }

    override fun observeTotalBalance(): Flow<Long> = observeAccounts().map { list -> list.sumOf { it.balance } }
    override suspend fun get(id: String): Account? = store.accounts.value.firstOrNull { it.id == id }
    override suspend fun save(account: Account): String {
        val withId = if (account.id.isBlank()) account.copy(id = store.id("acc")) else account
        store.accounts.update { list -> list.filterNot { it.id == withId.id } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.accounts.update { list -> list.filterNot { it.id == id } }
}

class FakeCategoryRepository(private val store: FakeStore) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = store.categories
    override fun observeCategories(type: CategoryType): Flow<List<Category>> = store.categories.map { l -> l.filter { it.type == type } }
    override fun observeAllIncludingDeleted(): Flow<List<Category>> = store.categories
    override fun observeByUsage(type: CategoryType, since: LocalDate): Flow<List<Category>> =
        combine(observeCategories(type), store.transactions) { cats, txs ->
            cats.sortedByDescending { c -> txs.count { it.categoryId == c.id } }
        }
    override suspend fun get(id: String): Category? = store.categories.value.firstOrNull { it.id == id }
    override suspend fun save(category: Category): String {
        val withId = if (category.id.isBlank()) category.copy(id = store.id("cat")) else category
        store.categories.update { list -> list.filterNot { it.id == withId.id } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.categories.update { list -> list.filterNot { it.id == id } }
}

class FakeTransactionRepository(private val store: FakeStore) : TransactionRepository {
    var lastFilter: TransactionFilter? = null

    private fun details(list: List<Transaction>, accounts: List<Account>, categories: List<Category>) =
        list.sortedByDescending { it.date }.map { tx ->
            TransactionDetails(
                tx,
                categories.firstOrNull { it.id == tx.categoryId },
                accounts.firstOrNull { it.id == tx.accountId },
                accounts.firstOrNull { it.id == tx.toAccountId },
            )
        }

    override fun observe(filter: TransactionFilter): Flow<List<TransactionDetails>> {
        lastFilter = filter
        return combine(store.transactions, store.accounts, store.categories) { txs, accs, cats ->
            details(
                txs.filter { tx ->
                    (filter.range == null || tx.date in filter.range!!) &&
                        (filter.type == null || tx.type == filter.type) &&
                        (filter.accountId == null || tx.accountId == filter.accountId || tx.toAccountId == filter.accountId) &&
                        (filter.categoryId == null || tx.categoryId == filter.categoryId) &&
                        (filter.query.isBlank() || tx.note.contains(filter.query, ignoreCase = true))
                },
                accs,
                cats,
            )
        }
    }

    override fun observeRecent(limit: Int): Flow<List<TransactionDetails>> =
        observe(TransactionFilter()).map { it.take(limit) }

    override fun observeTotals(range: DateRange): Flow<PeriodTotals> = store.transactions.map { txs ->
        val inRange = txs.filter { it.date in range }
        PeriodTotals(
            range.start,
            inRange.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            inRange.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
        )
    }

    override fun observeCategoryTotals(range: DateRange, type: TransactionType): Flow<List<CategoryTotal>> =
        combine(store.transactions, store.categories) { txs, cats ->
            txs.filter { it.date in range && it.type == type && it.categoryId != null }
                .groupBy { it.categoryId }
                .mapNotNull { (id, list) -> cats.firstOrNull { it.id == id }?.let { CategoryTotal(it, list.sumOf { tx -> tx.amount }) } }
                .sortedByDescending { it.total }
        }

    override fun observeBucketTotals(buckets: List<DateRange>): Flow<List<PeriodTotals>> = store.transactions.map { txs ->
        buckets.map { b ->
            val inRange = txs.filter { it.date in b }
            PeriodTotals(
                b.start,
                inRange.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                inRange.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            )
        }
    }

    override fun observeBalanceTrend(buckets: List<DateRange>): Flow<List<BalancePoint>> =
        observeBucketTotals(buckets).map { totals ->
            var running = 0L
            totals.map { running += it.net; BalancePoint(it.start, running) }
        }

    override suspend fun get(id: String): Transaction? = store.transactions.value.firstOrNull { it.id == id }
    override suspend fun save(transaction: Transaction): String {
        val withId = if (transaction.id.isBlank()) transaction.copy(id = store.id("tx")) else transaction
        store.transactions.update { list -> list.filterNot { it.id == withId.id } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.transactions.update { list -> list.filterNot { it.id == id } }
    override suspend fun allDetails(): List<TransactionDetails> =
        details(store.transactions.value, store.accounts.value, store.categories.value)
}

class FakeBudgetRepository(private val store: FakeStore) : BudgetRepository {
    override fun observeBudgets(): Flow<List<Budget>> = store.budgets
    override fun observeProgress(period: DateRange): Flow<List<BudgetProgress>> =
        combine(store.budgets, store.categories, store.transactions) { budgets, cats, txs ->
            budgets.mapNotNull { b ->
                val category = cats.firstOrNull { it.id == b.categoryId } ?: return@mapNotNull null
                val spent = txs.filter { it.categoryId == b.categoryId && it.type == TransactionType.EXPENSE && it.date in period }.sumOf { it.amount }
                BudgetProgress(b, category, spent)
            }
        }
    override suspend fun get(id: String): Budget? = store.budgets.value.firstOrNull { it.id == id }
    override suspend fun save(budget: Budget): String {
        val withId = if (budget.id.isBlank()) budget.copy(id = store.id("budget")) else budget
        store.budgets.update { list -> list.filterNot { it.id == withId.id || it.categoryId == withId.categoryId } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.budgets.update { list -> list.filterNot { it.id == id } }
}

class FakeRecurringRepository(private val store: FakeStore) : RecurringRepository {
    var processedUpTo: LocalDate? = null
    override fun observeRules(): Flow<List<RecurringRule>> = store.rules
    override suspend fun get(id: String): RecurringRule? = store.rules.value.firstOrNull { it.id == id }
    override suspend fun save(rule: RecurringRule): String {
        val withId = if (rule.id.isBlank()) rule.copy(id = store.id("rule")) else rule
        store.rules.update { list -> list.filterNot { it.id == withId.id } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.rules.update { list -> list.filterNot { it.id == id } }
    override suspend fun processDue(today: LocalDate): Int {
        processedUpTo = today
        return 0
    }
}

class FakeGoalRepository(private val store: FakeStore) : GoalRepository {
    override fun observeGoals(): Flow<List<SavingsGoal>> = store.goals
    override suspend fun get(id: String): SavingsGoal? = store.goals.value.firstOrNull { it.id == id }
    override suspend fun save(goal: SavingsGoal): String {
        val withId = if (goal.id.isBlank()) goal.copy(id = store.id("goal")) else goal
        store.goals.update { list -> list.filterNot { it.id == withId.id } + withId }
        return withId.id
    }
    override suspend fun delete(id: String) = store.goals.update { list -> list.filterNot { it.id == id } }
    override suspend fun adjust(id: String, delta: Long) = store.goals.update { list ->
        list.map { if (it.id == id) it.copy(saved = (it.saved + delta).coerceAtLeast(0)) else it }
    }
}
