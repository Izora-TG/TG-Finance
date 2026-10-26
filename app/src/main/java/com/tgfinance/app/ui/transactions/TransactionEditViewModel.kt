package com.tgfinance.app.ui.transactions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.CategoryRepository
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.money.AmountParser
import com.tgfinance.core.money.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TransactionForm(
    val id: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val recurringId: String? = null,
    val createdAt: Long = 0L,
)

data class TransactionEditUiState(
    val loaded: Boolean = false,
    val form: TransactionForm = TransactionForm(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    @StringRes val error: Int? = null,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = form.id == null
    val amountCents: Long? get() = AmountParser.parse(form.amountText)
    val canSave: Boolean
        get() = (amountCents ?: 0) > 0 && form.accountId != null && when (form.type) {
            TransactionType.TRANSFER -> form.toAccountId != null && form.toAccountId != form.accountId
            else -> form.categoryId != null
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val form = MutableStateFlow(TransactionForm(date = time.today()))
    private val meta = MutableStateFlow(Meta())

    private data class Meta(val loaded: Boolean = false, val error: Int? = null, val done: Boolean = false)

    private val categoryList = form.map { it.type }.distinctUntilChanged().flatMapLatest { type ->
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categories.observeByUsage(categoryType, time.today().minusDays(90))
    }

    private val accountList = accounts.observeAccounts().map { list -> list.map { it.account }.filterNot { it.archived } }

    val uiState: StateFlow<TransactionEditUiState> = combine(form, meta, categoryList, accountList) { f, m, cats, accs ->
        TransactionEditUiState(
            loaded = m.loaded,
            form = f,
            categories = cats,
            accounts = accs,
            today = time.today(),
            error = m.error,
            done = m.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionEditUiState(today = time.today()))

    private var loadedKey: String? = null

    /** Loads an existing booking or prepares a new one. Safe to call repeatedly. */
    fun load(id: String?, initialType: TransactionType?, initialAccountId: String?) {
        val key = "$id|$initialType|$initialAccountId"
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            if (id != null) {
                val tx = transactions.get(id)
                if (tx != null) {
                    form.value = TransactionForm(
                        id = tx.id,
                        type = tx.type,
                        amountText = MoneyFormatter().formatPlain(tx.amount),
                        categoryId = tx.categoryId,
                        accountId = tx.accountId,
                        toAccountId = tx.toAccountId,
                        date = tx.date,
                        note = tx.note,
                        recurringId = tx.recurringId,
                        createdAt = tx.createdAt,
                    )
                }
            } else {
                val accountsNow = accountList.first()
                val last = settings.settings.first().lastAccountId
                val account = initialAccountId
                    ?: last?.takeIf { lastId -> accountsNow.any { it.id == lastId } }
                    ?: accountsNow.firstOrNull()?.id
                form.value = TransactionForm(
                    type = initialType ?: TransactionType.EXPENSE,
                    accountId = account,
                    toAccountId = if (initialType == TransactionType.TRANSFER) accountsNow.firstOrNull { it.id != account }?.id else null,
                    date = time.today(),
                )
            }
            meta.update { it.copy(loaded = true) }
        }
    }

    fun setType(type: TransactionType) = form.update { current ->
        if (current.type == type) return@update current
        val accountsNow = uiState.value.accounts
        current.copy(
            type = type,
            // Income and expense categories differ, so the selection is reset.
            categoryId = null,
            toAccountId = if (type == TransactionType.TRANSFER) {
                current.toAccountId ?: accountsNow.firstOrNull { it.id != current.accountId }?.id
            } else {
                null
            },
        )
    }

    fun setAmount(text: String) {
        form.update { it.copy(amountText = text) }
        meta.update { it.copy(error = null) }
    }

    fun setCategory(id: String) = form.update { it.copy(categoryId = id) }
    fun setAccount(id: String) = form.update { it.copy(accountId = id) }
    fun setToAccount(id: String) = form.update { it.copy(toAccountId = id) }
    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }
    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun save() {
        val state = uiState.value
        val f = state.form
        val amount = state.amountCents
        val error = when {
            amount == null || amount <= 0 -> R.string.error_amount_invalid
            f.accountId == null -> R.string.error_account_required
            f.type == TransactionType.TRANSFER && (f.toAccountId == null || f.toAccountId == f.accountId) ->
                R.string.error_transfer_accounts
            f.type != TransactionType.TRANSFER && f.categoryId == null -> R.string.error_category_required
            else -> null
        }
        if (error != null) {
            meta.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            transactions.save(
                Transaction(
                    id = f.id.orEmpty(),
                    type = f.type,
                    amount = amount!!,
                    accountId = f.accountId!!,
                    toAccountId = f.toAccountId,
                    categoryId = f.categoryId,
                    date = f.date,
                    note = f.note.trim(),
                    recurringId = f.recurringId,
                    createdAt = f.createdAt,
                ),
            )
            settings.setLastAccountId(f.accountId!!)
            meta.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = form.value.id ?: return
        viewModelScope.launch {
            transactions.delete(id)
            meta.update { it.copy(done = true) }
        }
    }
}
