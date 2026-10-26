package com.tgfinance.app.ui.recurring

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.repository.RecurringRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.money.AmountParser
import com.tgfinance.core.money.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecurringItem(
    val rule: RecurringRule,
    val category: Category?,
    val account: Account?,
)

data class RecurringUiState(
    val loading: Boolean = true,
    val items: List<RecurringItem> = emptyList(),
) {
    /** Approximate monthly effect of all active rules (income minus expenses). */
    val monthlyNet: Long
        get() = items.filter { it.rule.active }.sumOf { item ->
            val perMonth = when (item.rule.frequency) {
                Frequency.DAILY -> item.rule.amount * 30 / item.rule.interval
                Frequency.WEEKLY -> item.rule.amount * 52 / 12 / item.rule.interval
                Frequency.MONTHLY -> item.rule.amount / item.rule.interval
                Frequency.YEARLY -> item.rule.amount / 12 / item.rule.interval
            }
            when (item.rule.type) {
                TransactionType.INCOME -> perMonth
                TransactionType.EXPENSE -> -perMonth
                TransactionType.TRANSFER -> 0
            }
        }
}

@HiltViewModel
class RecurringViewModel @Inject constructor(
    recurring: RecurringRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
) : ViewModel() {
    val uiState: StateFlow<RecurringUiState> = combine(
        recurring.observeRules(),
        categories.observeAllIncludingDeleted(),
        accounts.observeAccounts().map { list -> list.map { it.account } },
    ) { rules, cats, accs ->
        val categoryMap = cats.associateBy { it.id }
        val accountMap = accs.associateBy { it.id }
        RecurringUiState(
            loading = false,
            items = rules.map { RecurringItem(it, it.categoryId?.let(categoryMap::get), accountMap[it.accountId]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())
}

data class RecurringForm(
    val id: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val note: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val nextDate: LocalDate = LocalDate.now(),
    val active: Boolean = true,
)

data class RecurringEditUiState(
    val form: RecurringForm = RecurringForm(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    @StringRes val error: Int? = null,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = form.id == null
}

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val form = MutableStateFlow(RecurringForm(startDate = time.today(), nextDate = time.today()))
    private val meta = MutableStateFlow<Pair<Int?, Boolean>>(null to false)

    val uiState: StateFlow<RecurringEditUiState> = combine(
        form,
        meta,
        categories.observeCategories(),
        accounts.observeAccounts().map { list -> list.map { it.account }.filterNot { it.archived } },
    ) { f, m, cats, accs ->
        val type = if (f.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        RecurringEditUiState(
            form = if (f.accountId == null && accs.isNotEmpty()) f.copy(accountId = accs.first().id) else f,
            categories = cats.filter { it.type == type },
            accounts = accs,
            error = m.first,
            done = m.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringEditUiState())

    private var loadedId: String? = "unset"

    fun load(id: String?) {
        if (loadedId == id) return
        loadedId = id
        if (id == null) return
        viewModelScope.launch {
            val rule = recurring.get(id) ?: return@launch
            form.value = RecurringForm(
                id = rule.id,
                type = rule.type,
                amountText = MoneyFormatter().formatPlain(rule.amount),
                categoryId = rule.categoryId,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId,
                note = rule.note,
                frequency = rule.frequency,
                interval = rule.interval,
                startDate = rule.startDate,
                endDate = rule.endDate,
                nextDate = rule.nextDate,
                active = rule.active,
            )
        }
    }

    fun update(transform: (RecurringForm) -> RecurringForm) {
        form.update(transform)
        meta.update { null to it.second }
    }

    fun setType(type: TransactionType) = update {
        if (it.type == type) it else it.copy(type = type, categoryId = null, toAccountId = null)
    }

    fun save() {
        val f = uiState.value.form
        val amount = AmountParser.parse(f.amountText)
        val error = when {
            amount == null || amount <= 0 -> R.string.error_amount_invalid
            f.accountId == null -> R.string.error_account_required
            f.type == TransactionType.TRANSFER && (f.toAccountId == null || f.toAccountId == f.accountId) -> R.string.error_transfer_accounts
            f.type != TransactionType.TRANSFER && f.categoryId == null -> R.string.error_category_required
            f.endDate != null && f.endDate.isBefore(f.startDate) -> R.string.error_end_before_start
            else -> null
        }
        if (error != null) {
            meta.value = error to false
            return
        }
        viewModelScope.launch {
            recurring.save(
                RecurringRule(
                    id = f.id.orEmpty(),
                    type = f.type,
                    amount = amount!!,
                    accountId = f.accountId!!,
                    toAccountId = f.toAccountId.takeIf { f.type == TransactionType.TRANSFER },
                    categoryId = f.categoryId.takeIf { f.type != TransactionType.TRANSFER },
                    note = f.note.trim(),
                    frequency = f.frequency,
                    interval = f.interval.coerceIn(1, 365),
                    startDate = f.startDate,
                    endDate = f.endDate,
                    nextDate = f.nextDate,
                    active = f.active,
                ),
            )
            // Book anything that is already due (e.g. a start date in the past) right away.
            recurring.processDue(time.today())
            meta.value = null to true
        }
    }

    fun delete() {
        val id = form.value.id ?: return
        viewModelScope.launch {
            recurring.delete(id)
            meta.value = null to true
        }
    }
}
