package com.tgfinance.app.ui.transactions

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountField
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.DateChips
import com.tgfinance.app.ui.components.FinanceIcons
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.TransactionType
import java.time.LocalDate

data class TransactionEditActions(
    val onTypeChange: (TransactionType) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onCategoryChange: (String) -> Unit = {},
    val onAccountChange: (String) -> Unit = {},
    val onToAccountChange: (String) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClose: () -> Unit = {},
)

@Composable
fun TransactionEditRoute(
    id: String?,
    initialType: TransactionType?,
    initialAccountId: String?,
    onDone: () -> Unit,
    viewModelKey: String? = null,
    viewModel: TransactionEditViewModel = hiltViewModel(key = viewModelKey),
) {
    LaunchedEffect(id, initialType, initialAccountId) { viewModel.load(id, initialType, initialAccountId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    TransactionEditScreen(
        state = state,
        actions = TransactionEditActions(
            onTypeChange = viewModel::setType,
            onAmountChange = viewModel::setAmount,
            onCategoryChange = viewModel::setCategory,
            onAccountChange = viewModel::setAccount,
            onToAccountChange = viewModel::setToAccount,
            onDateChange = viewModel::setDate,
            onNoteChange = viewModel::setNote,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onClose = onDone,
        ),
    )
}

/**
 * Quick entry: the amount field is focused immediately, the most used categories come first.
 * Typical flow = FAB → type amount → tap category → save (3 taps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    state: TransactionEditUiState,
    actions: TransactionEditActions,
) {
    val form = state.form
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded && state.isNew) runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.transaction_new else R.string.transaction_edit))
                },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = actions.onSave, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            TypeSelector(form.type, actions.onTypeChange)

            AmountField(
                value = form.amountText,
                onValueChange = actions.onAmountChange,
                modifier = Modifier.focusRequester(focusRequester),
                isError = state.error == R.string.error_amount_invalid,
                imeAction = ImeAction.Done,
                onDone = { if (state.canSave) actions.onSave() },
            )

            if (form.type == TransactionType.TRANSFER) {
                SectionLabel(stringResource(R.string.field_from_account))
                AccountChips(state.accounts, form.accountId, actions.onAccountChange)
                SectionLabel(stringResource(R.string.field_to_account))
                AccountChips(state.accounts.filter { it.id != form.accountId }, form.toAccountId, actions.onToAccountChange)
            } else {
                SectionLabel(stringResource(R.string.field_category))
                CategoryGrid(state.categories, form.categoryId, actions.onCategoryChange)
                SectionLabel(stringResource(R.string.field_account))
                AccountChips(state.accounts, form.accountId, actions.onAccountChange)
            }

            SectionLabel(stringResource(R.string.field_date))
            DateChips(date = form.date, today = state.today, onDateChange = actions.onDateChange)

            OutlinedTextField(
                value = form.note,
                onValueChange = actions.onNoteChange,
                label = { Text(stringResource(R.string.field_note)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                maxLines = 3,
            )

            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.finance.expense, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = actions.onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.height(Spacing.l))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            val color = when (type) {
                TransactionType.INCOME -> MaterialTheme.finance.incomeContainer
                TransactionType.EXPENSE -> MaterialTheme.finance.expenseContainer
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.primaryContainer
            }
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = color),
            ) { Text(typeLabel(type)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        categories.forEach { category ->
            val color = Color(category.color)
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(FinanceIcons.forKey(category.icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun AccountChips(accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        accounts.forEach { account ->
            FilterChip(
                selected = account.id == selectedId,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
                leadingIcon = {
                    Icon(FinanceIcons.forKey(account.icon), contentDescription = null, tint = Color(account.color), modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

private val previewState = TransactionEditUiState(
    loaded = true,
    form = TransactionForm(amountText = "12,50", categoryId = "c1", accountId = "a1", date = LocalDate.of(2024, 5, 15)),
    categories = PreviewData.categories.filter { it.type == com.tgfinance.core.model.CategoryType.EXPENSE },
    accounts = PreviewData.accounts.map { it.account },
    today = LocalDate.of(2024, 5, 15),
)

@Preview(device = Devices.PIXEL_7)
@Composable
private fun TransactionEditPreview() {
    TgFinanceTheme { TransactionEditScreen(previewState, TransactionEditActions()) }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun TransferEditPreview() {
    TgFinanceTheme {
        TransactionEditScreen(
            previewState.copy(form = previewState.form.copy(id = "t4", type = TransactionType.TRANSFER, toAccountId = "a3")),
            TransactionEditActions(),
        )
    }
}
