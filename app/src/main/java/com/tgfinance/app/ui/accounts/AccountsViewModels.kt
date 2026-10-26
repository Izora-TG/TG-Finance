package com.tgfinance.app.ui.accounts

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.R
import com.tgfinance.app.data.repository.AccountRepository
import com.tgfinance.app.data.repository.TransactionFilter
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.ui.components.FinanceIcons
import com.tgfinance.app.ui.theme.FinanceColors
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.AccountWithBalance
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.money.AmountParser
import com.tgfinance.core.money.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val loading: Boolean = true,
    val accounts: List<AccountWithBalance> = emptyList(),
) {
    val total: Long get() = accounts.sumOf { it.balance }
    val active: List<AccountWithBalance> get() = accounts.filterNot { it.account.archived }
    val archived: List<AccountWithBalance> get() = accounts.filter { it.account.archived }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accounts: AccountRepository,
) : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = accounts.observeAccounts()
        .map { AccountsUiState(loading = false, accounts = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val transactions: List<TransactionDetails> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    accounts: AccountRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    private val accountId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountDetailUiState> = accountId.filterNotNull().flatMapLatest { id ->
        combine(
            accounts.observeAccounts().map { list -> list.firstOrNull { it.account.id == id } },
            transactions.observe(TransactionFilter(accountId = id)),
        ) { account, list -> AccountDetailUiState(account, list) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

    fun load(id: String) {
        accountId.value = id
    }
}

data class AccountForm(
    val id: String? = null,
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val initialBalanceText: String = "",
    val negativeInitial: Boolean = false,
    val color: Int = FinanceColors.PrimaryArgb.toInt(),
    val icon: String = FinanceIcons.defaultFor(AccountType.CHECKING),
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

data class AccountEditUiState(
    val form: AccountForm = AccountForm(),
    @StringRes val error: Int? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = form.id == null
    val canSave: Boolean get() = form.name.isNotBlank()
}

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountEditUiState())
    val uiState: StateFlow<AccountEditUiState> = _state

    private var loadedId: String? = "unset"

    fun load(id: String?) {
        if (loadedId == id) return
        loadedId = id
        if (id == null) return
        viewModelScope.launch {
            val account = accounts.get(id) ?: return@launch
            _state.update {
                it.copy(
                    form = AccountForm(
                        id = account.id,
                        name = account.name,
                        type = account.type,
                        initialBalanceText = if (account.initialBalance == 0L) "" else MoneyFormatter().formatPlain(kotlin.math.abs(account.initialBalance)),
                        negativeInitial = account.initialBalance < 0,
                        color = account.color,
                        icon = account.icon,
                        archived = account.archived,
                        sortOrder = account.sortOrder,
                    ),
                )
            }
        }
    }

    fun update(transform: (AccountForm) -> AccountForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun setType(type: AccountType) = update { form ->
        // Follow the type with the icon unless the user picked a custom one.
        val iconWasDefault = form.icon == FinanceIcons.defaultFor(form.type)
        form.copy(type = type, icon = if (iconWasDefault) FinanceIcons.defaultFor(type) else form.icon)
    }

    fun save() {
        val form = _state.value.form
        if (form.name.isBlank()) {
            _state.update { it.copy(error = R.string.error_name_required) }
            return
        }
        val initial = if (form.initialBalanceText.isBlank()) 0L else AmountParser.parse(form.initialBalanceText)
        if (initial == null) {
            _state.update { it.copy(error = R.string.error_amount_invalid) }
            return
        }
        viewModelScope.launch {
            accounts.save(
                Account(
                    id = form.id.orEmpty(),
                    name = form.name.trim(),
                    type = form.type,
                    initialBalance = if (form.negativeInitial) -initial else initial,
                    color = form.color,
                    icon = form.icon,
                    sortOrder = form.sortOrder,
                    archived = form.archived,
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.form.id ?: return
        viewModelScope.launch {
            accounts.delete(id)
            _state.update { it.copy(deleted = true) }
        }
    }
}
