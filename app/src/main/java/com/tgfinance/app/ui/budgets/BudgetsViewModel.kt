package com.tgfinance.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.data.repository.BudgetRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.Budget
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.money.AmountParser
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val loading: Boolean = true,
    val period: DateRange? = null,
    val isCurrentPeriod: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList(),
    /** Expense categories that have no budget yet. */
    val availableCategories: List<Category> = emptyList(),
) {
    val totalBudget: Long get() = budgets.sumOf { it.budget.amount }
    val totalSpent: Long get() = budgets.sumOf { it.spent }
    val totalRatio: Float get() = if (totalBudget > 0) totalSpent.toFloat() / totalBudget else 0f
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    /** 0 = current month, -1 = previous month, … */
    private val offset = MutableStateFlow(0)

    private val period = combine(offset, settings.settings.map { it.monthStartDay }) { o, startDay ->
        PeriodCalculator.month(time.today().plusMonths(o.toLong()), startDay) to (o == 0)
    }

    val uiState: StateFlow<BudgetsUiState> = period.flatMapLatest { (range, current) ->
        combine(budgets.observeProgress(range), categories.observeCategories(CategoryType.EXPENSE)) { progress, cats ->
            val used = progress.map { it.category.id }.toSet()
            BudgetsUiState(
                loading = false,
                period = range,
                isCurrentPeriod = current,
                budgets = progress,
                availableCategories = cats.filterNot { it.id in used },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun previousPeriod() {
        offset.value -= 1
    }

    fun nextPeriod() {
        if (offset.value < 0) offset.value += 1
    }

    /** Returns false if the amount is invalid. */
    fun save(budgetId: String?, categoryId: String, amountText: String): Boolean {
        val amount = AmountParser.parse(amountText)?.takeIf { it > 0 } ?: return false
        viewModelScope.launch { budgets.save(Budget(budgetId.orEmpty(), categoryId, amount)) }
        return true
    }

    fun delete(id: String) {
        viewModelScope.launch { budgets.delete(id) }
    }
}
