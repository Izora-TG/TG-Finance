package com.tgfinance.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.BalancePoint
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.PeriodTotals
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodCalculator
import com.tgfinance.core.time.PeriodType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class StatisticsSelection(
    val periodType: PeriodType = PeriodType.MONTH,
    /** Number of periods into the past (0 = current). */
    val offset: Int = 0,
    val categoryType: TransactionType = TransactionType.EXPENSE,
)

data class StatisticsUiState(
    val loading: Boolean = true,
    val selection: StatisticsSelection = StatisticsSelection(),
    val range: DateRange? = null,
    val totals: PeriodTotals? = null,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val buckets: List<PeriodTotals> = emptyList(),
    val bucketsAreDays: Boolean = true,
    val balanceTrend: List<BalancePoint> = emptyList(),
) {
    val categorySum: Long get() = categoryTotals.sumOf { it.total }
    val savingsRate: Float?
        get() = totals?.takeIf { it.income > 0 }?.let { it.net.toFloat() / it.income }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val selection = MutableStateFlow(StatisticsSelection())

    val uiState: StateFlow<StatisticsUiState> = combine(selection, settings.settings) { sel, s -> sel to s }
        .flatMapLatest { (sel, s) ->
            val anchor = PeriodCalculator.shift(sel.periodType, time.today(), -sel.offset)
            val range = PeriodCalculator.range(sel.periodType, anchor, s.monthStartDay, s.firstDayOfWeek)
            val buckets = PeriodCalculator.buckets(range, s.monthStartDay)
            combine(
                transactions.observeTotals(range),
                transactions.observeCategoryTotals(range, sel.categoryType),
                transactions.observeBucketTotals(buckets),
                transactions.observeBalanceTrend(buckets),
            ) { totals, categories, bucketTotals, trend ->
                StatisticsUiState(
                    loading = false,
                    selection = sel,
                    range = range,
                    totals = totals,
                    categoryTotals = categories,
                    buckets = bucketTotals,
                    bucketsAreDays = range.days <= 31,
                    balanceTrend = trend,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun setPeriodType(type: PeriodType) = selection.update { it.copy(periodType = type, offset = 0) }
    fun previous() = selection.update { it.copy(offset = it.offset + 1) }
    fun next() = selection.update { it.copy(offset = (it.offset - 1).coerceAtLeast(0)) }
    fun setCategoryType(type: TransactionType) = selection.update { it.copy(categoryType = type) }
}
