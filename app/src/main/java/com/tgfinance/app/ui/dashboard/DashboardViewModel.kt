package com.tgfinance.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.BudgetRepository
import com.tgfinance.app.data.repository.GoalRepository
import com.tgfinance.app.data.repository.RecurringRepository
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.data.sync.SyncController
import com.tgfinance.app.data.sync.SyncStatus
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.PeriodTotals
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val totalBalance: Long = 0,
    val period: DateRange? = null,
    val totals: PeriodTotals? = null,
    val topCategories: List<CategoryTotal> = emptyList(),
    val recent: List<TransactionDetails> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
) {
    val overBudget: List<BudgetProgress> get() = budgets.filter { it.isOver }
    val expenseTotal: Long get() = topCategories.sumOf { it.total }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    goals: GoalRepository,
    settings: SettingsRepository,
    private val recurring: RecurringRepository,
    private val time: TimeProvider,
    private val sync: SyncController,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> =
        sync.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val period = settings.settings
        .map { PeriodCalculator.month(time.today(), it.monthStartDay) }
        .distinctUntilChanged()

    private val periodData = period.flatMapLatest { range ->
        combine(
            transactions.observeTotals(range),
            transactions.observeCategoryTotals(range, TransactionType.EXPENSE),
            budgets.observeProgress(range),
        ) { totals, categories, budgetProgress -> Triple(range, totals, categories to budgetProgress) }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        accounts.observeTotalBalance(),
        periodData,
        transactions.observeRecent(RECENT_COUNT),
        goals.observeGoals(),
    ) { balance, (range, totals, rest), recent, goalList ->
        DashboardUiState(
            loading = false,
            totalBalance = balance,
            period = range,
            totals = totals,
            topCategories = rest.first,
            recent = recent,
            budgets = rest.second,
            goals = goalList.filterNot { it.isReached }.take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Pull-to-refresh: books due recurring transactions and syncs with the server (if enabled). */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                recurring.processDue(time.today())
                sync.syncNow()
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        const val RECENT_COUNT = 5
    }
}
