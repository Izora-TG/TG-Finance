package com.tgfinance.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.repository.TransactionFilter
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class PeriodPreset { ALL, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, THIS_YEAR }

data class FilterState(
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val period: PeriodPreset = PeriodPreset.ALL,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || accountId != null || categoryId != null || period != PeriodPreset.ALL
}

data class DayGroup(
    val date: LocalDate,
    val net: Long,
    val items: List<TransactionDetails>,
)

data class TransactionsUiState(
    val loading: Boolean = true,
    val filter: FilterState = FilterState(),
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(FilterState())

    /** Last deleted booking, kept for "undo". */
    private val _lastDeleted = MutableStateFlow<Transaction?>(null)
    val lastDeleted: StateFlow<Transaction?> = _lastDeleted.asStateFlow()

    private val results = combine(filter.debounce(150), settings.settings) { f, s -> f to s.monthStartDay }
        .flatMapLatest { (f, monthStart) ->
            transactions.observe(
                TransactionFilter(
                    range = rangeFor(f.period, monthStart),
                    type = f.type,
                    accountId = f.accountId,
                    categoryId = f.categoryId,
                    query = f.query,
                ),
            )
        }

    val uiState: StateFlow<TransactionsUiState> = combine(
        filter,
        results,
        accounts.observeAccounts().map { list -> list.map { it.account } },
        categories.observeCategories(),
    ) { f, list, accountList, categoryList ->
        TransactionsUiState(
            loading = false,
            filter = f,
            groups = group(list),
            count = list.size,
            income = list.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount },
            expense = list.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount },
            accounts = accountList,
            categories = categoryList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setType(type: TransactionType?) = filter.update { it.copy(type = type) }
    fun setAccount(id: String?) = filter.update { it.copy(accountId = id) }
    fun setCategory(id: String?) = filter.update { it.copy(categoryId = id) }
    fun setPeriod(period: PeriodPreset) = filter.update { it.copy(period = period) }
    fun clearFilters() = filter.update { FilterState() }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            transactions.delete(transaction.id)
            _lastDeleted.value = transaction
        }
    }

    fun undoDelete() {
        val tx = _lastDeleted.value ?: return
        viewModelScope.launch {
            transactions.save(tx)
            _lastDeleted.value = null
        }
    }

    fun dismissUndo() {
        _lastDeleted.value = null
    }

    private fun rangeFor(preset: PeriodPreset, monthStartDay: Int): DateRange? {
        val today = time.today()
        return when (preset) {
            PeriodPreset.ALL -> null
            PeriodPreset.THIS_MONTH -> PeriodCalculator.month(today, monthStartDay)
            PeriodPreset.LAST_MONTH -> PeriodCalculator.month(today.minusMonths(1), monthStartDay)
            PeriodPreset.LAST_3_MONTHS -> DateRange(
                PeriodCalculator.month(today.minusMonths(2), monthStartDay).start,
                PeriodCalculator.month(today, monthStartDay).endInclusive,
            )
            PeriodPreset.THIS_YEAR -> PeriodCalculator.year(today, monthStartDay)
        }
    }

    companion object {
        fun group(list: List<TransactionDetails>): List<DayGroup> =
            list.groupBy { it.transaction.date }
                .map { (date, items) -> DayGroup(date, items.sumOf { it.transaction.signedAmount }, items) }
                .sortedByDescending { it.date }
    }
}
