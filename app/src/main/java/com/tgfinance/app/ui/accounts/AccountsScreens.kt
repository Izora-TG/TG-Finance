package com.tgfinance.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountText
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.TransactionRow
import com.tgfinance.app.ui.navigation.LocalAdaptiveInfo
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.accountTypeLabel
import com.tgfinance.core.model.AccountWithBalance

@Composable
fun AccountsRoute(
    onAddAccount: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onEditAccount: (String) -> Unit,
    onTransfer: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptive = LocalAdaptiveInfo.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(adaptive.twoPane, state.accounts) {
        // In two-pane mode always show a detail; drop the selection if the account vanished.
        if (adaptive.twoPane && (selected == null || state.accounts.none { it.account.id == selected })) {
            selected = state.accounts.firstOrNull()?.account?.id
        }
    }
    if (adaptive.twoPane) {
        Row(Modifier.fillMaxSize()) {
            AccountsScreen(
                state = state,
                onAddAccount = onAddAccount,
                onOpenAccount = { selected = it },
                onTransfer = onTransfer,
                selectedId = selected,
                showFab = false,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider()
            Box(
                Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
            ) {
                selected?.let { id ->
                    AccountDetailRoute(
                        id = id,
                        onBack = null,
                        onEdit = { onEditAccount(id) },
                        onOpenTransaction = onOpenTransaction,
                        onAddTransaction = { onAddTransaction(id) },
                        viewModel = hiltViewModel(key = "account-detail-$id"),
                    )
                }
            }
        }
    } else {
        AccountsScreen(
            state = state,
            onAddAccount = onAddAccount,
            onOpenAccount = onOpenAccount,
            onTransfer = onTransfer,
            showFab = !adaptive.useRail,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onAddAccount: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    showFab: Boolean = true,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_accounts)) },
                actions = {
                    IconButton(onClick = onTransfer, enabled = state.active.size >= 2) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = stringResource(R.string.action_transfer))
                    }
                    if (!showFab) {
                        IconButton(onClick = onAddAccount) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.account_add))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = onAddAccount,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.account_add)) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xl),
                    ) {
                        Text(
                            stringResource(R.string.dashboard_total_balance),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                        AmountText(
                            cents = state.total,
                            style = MaterialTheme.typography.headlineLarge,
                            color = if (state.total < 0) MaterialTheme.finance.expense else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (state.accounts.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.accounts_empty_title),
                        message = stringResource(R.string.accounts_empty_message),
                        action = { TextButton(onClick = onAddAccount) { Text(stringResource(R.string.account_add)) } },
                    )
                }
            }
            items(state.active, key = { it.account.id }) { item ->
                AccountCard(item, selected = item.account.id == selectedId, onClick = { onOpenAccount(item.account.id) })
            }
            if (state.archived.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.accounts_archived),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
                items(state.archived, key = { it.account.id }) { item ->
                    AccountCard(item, selected = item.account.id == selectedId, onClick = { onOpenAccount(item.account.id) })
                }
            }
            if (state.active.size >= 2) {
                item {
                    OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(R.string.action_transfer))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(item: AccountWithBalance, selected: Boolean, onClick: () -> Unit) {
    val account = item.account
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColoredIcon(account.icon, account.color, size = 44.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.m),
            ) {
                Text(account.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    accountTypeLabel(account.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(
                cents = item.balance,
                color = if (item.balance < 0) MaterialTheme.finance.expense else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun AccountDetailRoute(
    id: String,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountDetailScreen(state, onBack, onEdit, onOpenTransaction, onAddTransaction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
) {
    val account = state.account
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.account?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_transaction)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(Modifier.padding(Spacing.l)) {
                    if (account != null) {
                        Text(
                            accountTypeLabel(account.account.type),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AmountText(
                            cents = account.balance,
                            style = MaterialTheme.typography.displaySmall,
                            color = if (account.balance < 0) MaterialTheme.finance.expense else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.account_initial_balance_value, LocalMoneyFormatter.current.format(account.account.initialBalance)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.account_no_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.l),
                    )
                }
            }
            items(state.transactions, key = { it.transaction.id }) { details ->
                TransactionRow(details, onClick = { onOpenTransaction(details.transaction.id) })
            }
        }
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun AccountsPreview() {
    TgFinanceTheme {
        AccountsScreen(AccountsUiState(false, PreviewData.accounts), onAddAccount = {}, onOpenAccount = {}, onTransfer = {})
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun AccountDetailPreview() {
    TgFinanceTheme {
        AccountDetailScreen(
            AccountDetailUiState(PreviewData.accounts.first(), PreviewData.transactions),
            onBack = {},
            onEdit = {},
            onOpenTransaction = {},
            onAddTransaction = {},
        )
    }
}
