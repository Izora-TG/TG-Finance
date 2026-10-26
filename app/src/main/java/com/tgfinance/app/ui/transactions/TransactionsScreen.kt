package com.tgfinance.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountText
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.TransactionRow
import com.tgfinance.app.ui.navigation.LocalAdaptiveInfo
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.relativeDayLabel
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType

private const val NEW_ITEM = "__new__"

@Composable
fun TransactionsRoute(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()
    val adaptive = LocalAdaptiveInfo.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var paneGeneration by rememberSaveable { mutableIntStateOf(0) }

    val actions = TransactionsActions(
        onQueryChange = viewModel::setQuery,
        onTypeChange = viewModel::setType,
        onAccountChange = viewModel::setAccount,
        onCategoryChange = viewModel::setCategory,
        onPeriodChange = viewModel::setPeriod,
        onClearFilters = viewModel::clearFilters,
        onDelete = viewModel::delete,
        onUndo = viewModel::undoDelete,
        onUndoDismissed = viewModel::dismissUndo,
        onAdd = if (adaptive.twoPane) ({ selected = NEW_ITEM; paneGeneration++ }) else onAdd,
        onOpen = if (adaptive.twoPane) ({ selected = it; paneGeneration++ }) else onOpen,
    )

    if (adaptive.twoPane) {
        Row(Modifier.fillMaxSize()) {
            TransactionsScreen(
                state = state,
                lastDeleted = lastDeleted,
                actions = actions,
                selectedId = selected,
                showFab = false,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider()
            Box(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
            ) {
                val current = selected
                if (current == null) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.transactions_pane_title),
                        message = stringResource(R.string.transactions_pane_message),
                        modifier = Modifier.align(Alignment.Center),
                        action = { TextButton(onClick = actions.onAdd) { Text(stringResource(R.string.action_add_transaction)) } },
                    )
                } else {
                    TransactionEditRoute(
                        id = current.takeIf { it != NEW_ITEM },
                        initialType = null,
                        initialAccountId = null,
                        onDone = { selected = null },
                        viewModelKey = "tx-edit-$current-$paneGeneration",
                    )
                }
            }
        }
    } else {
        TransactionsScreen(state = state, lastDeleted = lastDeleted, actions = actions, showFab = !adaptive.useRail)
    }
}

data class TransactionsActions(
    val onQueryChange: (String) -> Unit = {},
    val onTypeChange: (TransactionType?) -> Unit = {},
    val onAccountChange: (String?) -> Unit = {},
    val onCategoryChange: (String?) -> Unit = {},
    val onPeriodChange: (PeriodPreset) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onDelete: (Transaction) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoDismissed: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    lastDeleted: Transaction?,
    actions: TransactionsActions,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    showFab: Boolean = true,
) {
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(lastDeleted) {
        if (lastDeleted != null) {
            val result = snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) actions.onUndo() else actions.onUndoDismissed()
        }
    }
    val formatter = LocalMoneyFormatter.current

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_transactions)) },
                    actions = {
                        if (!showFab) {
                            IconButton(onClick = actions.onAdd) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_transaction))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                SearchField(state.filter.query, actions.onQueryChange)
                FilterRow(state, actions)
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = actions.onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_transaction)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.groups.isEmpty() && !state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.filter.isActive) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.transactions_no_results_title),
                        message = stringResource(R.string.transactions_no_results_message),
                        action = { TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.action_reset_filters)) } },
                    )
                } else {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.transactions_empty_title),
                        message = stringResource(R.string.transactions_empty_message),
                        action = { TextButton(onClick = actions.onAdd) { Text(stringResource(R.string.action_add_transaction)) } },
                    )
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (state.filter.isActive) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    ) {
                        Text(
                            stringResource(R.string.transactions_result_summary, state.count),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "+${formatter.format(state.income)}  −${formatter.format(state.expense)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "header-${group.date}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            relativeDayLabel(group.date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        AmountText(
                            cents = group.net,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group.items, key = { it.transaction.id }) { details ->
                    SwipeToDelete(onDelete = { actions.onDelete(details.transaction) }) {
                        TransactionRow(
                            details = details,
                            onClick = { actions.onOpen(details.transaction.id) },
                            selected = details.transaction.id == selectedId,
                            modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun FilterRow(state: TransactionsUiState, actions: TransactionsActions) {
    val filter = state.filter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        val allLabel = stringResource(R.string.filter_all)
        DropdownChip(
            label = filter.period.takeIf { it != PeriodPreset.ALL }?.let { periodPresetLabel(it) } ?: stringResource(R.string.filter_period),
            selected = filter.period != PeriodPreset.ALL,
            options = PeriodPreset.entries.map { preset ->
                (if (preset == PeriodPreset.ALL) allLabel else periodPresetLabel(preset)) to { actions.onPeriodChange(preset) }
            },
        )
        DropdownChip(
            label = filter.type?.let { typeLabel(it) } ?: stringResource(R.string.filter_type),
            selected = filter.type != null,
            options = listOf(allLabel to { actions.onTypeChange(null) }) +
                TransactionType.entries.map { type -> typeLabel(type) to { actions.onTypeChange(type) } },
        )
        DropdownChip(
            label = state.accounts.firstOrNull { it.id == filter.accountId }?.name ?: stringResource(R.string.filter_account),
            selected = filter.accountId != null,
            options = listOf(allLabel to { actions.onAccountChange(null) }) +
                state.accounts.map { account -> account.name to { actions.onAccountChange(account.id) } },
        )
        DropdownChip(
            label = state.categories.firstOrNull { it.id == filter.categoryId }?.name ?: stringResource(R.string.filter_category),
            selected = filter.categoryId != null,
            options = listOf(allLabel to { actions.onCategoryChange(null) }) +
                state.categories.map { category -> category.name to { actions.onCategoryChange(category.id) } },
        )
        if (filter.isActive) {
            TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.action_reset_filters)) }
        }
    }
}

@Composable
private fun DropdownChip(label: String, selected: Boolean, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    action()
                    open = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.finance.expenseContainer)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.finance.expense)
            }
        },
    ) { content() }
}

@Composable
fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.transaction_type_income
        TransactionType.EXPENSE -> R.string.transaction_type_expense
        TransactionType.TRANSFER -> R.string.transaction_type_transfer
    },
)

@Composable
private fun periodPresetLabel(preset: PeriodPreset): String = stringResource(
    when (preset) {
        PeriodPreset.ALL -> R.string.filter_all
        PeriodPreset.THIS_MONTH -> R.string.period_this_month
        PeriodPreset.LAST_MONTH -> R.string.period_last_month
        PeriodPreset.LAST_3_MONTHS -> R.string.period_last_3_months
        PeriodPreset.THIS_YEAR -> R.string.period_this_year
    },
)

private val previewState = TransactionsUiState(
    loading = false,
    groups = TransactionsViewModel.group(PreviewData.transactions),
    count = PreviewData.transactions.size,
    accounts = PreviewData.accounts.map { it.account },
    categories = PreviewData.categories,
)

@Preview(device = Devices.PIXEL_7)
@Composable
private fun TransactionsPreview() {
    TgFinanceTheme { TransactionsScreen(previewState, lastDeleted = null, actions = TransactionsActions()) }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun TransactionsFilteredEmptyPreview() {
    TgFinanceTheme {
        TransactionsScreen(
            TransactionsUiState(loading = false, filter = FilterState(query = "Kino")),
            lastDeleted = null,
            actions = TransactionsActions(),
        )
    }
}
