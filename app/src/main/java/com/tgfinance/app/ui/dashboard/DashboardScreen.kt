package com.tgfinance.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountText
import com.tgfinance.app.ui.components.BudgetBar
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.ExpandableCard
import com.tgfinance.app.ui.components.LabeledAmount
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.components.TransactionRow
import com.tgfinance.app.ui.navigation.LocalAdaptiveInfo
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.sync.SyncStatusIndicator
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.periodLabel
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.CategoryTotal
import com.tgfinance.core.model.SavingsGoal
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodType
import java.time.LocalDate

data class DashboardActions(
    val onAddTransaction: () -> Unit = {},
    val onOpenTransaction: (String) -> Unit = {},
    val onSeeAllTransactions: () -> Unit = {},
    val onOpenBudgets: () -> Unit = {},
    val onOpenGoals: () -> Unit = {},
    val onOpenAccounts: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenSync: () -> Unit = {},
    val onRefresh: () -> Unit = {},
)

@Composable
fun DashboardRoute(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        refreshing = refreshing,
        actions = DashboardActions(
            onAddTransaction = onAddTransaction,
            onOpenTransaction = onOpenTransaction,
            onSeeAllTransactions = onSeeAllTransactions,
            onOpenBudgets = onOpenBudgets,
            onOpenGoals = onOpenGoals,
            onOpenAccounts = onOpenAccounts,
            onOpenSettings = onOpenSettings,
            onOpenSync = onOpenSync,
            onRefresh = viewModel::refresh,
        ),
        statusContent = { SyncStatusIndicator(syncStatus, onClick = onOpenSync) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    refreshing: Boolean,
    actions: DashboardActions,
    statusContent: @Composable () -> Unit = {},
) {
    val adaptive = LocalAdaptiveInfo.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_dashboard)) },
                actions = {
                    statusContent()
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (!adaptive.useRail) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAddTransaction,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_transaction_short)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                verticalItemSpacing = Spacing.l,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    BalanceCard(state, onClick = actions.onOpenAccounts)
                }
                if (state.overBudget.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        OverBudgetBanner(state.overBudget, onClick = actions.onOpenBudgets)
                    }
                }
                item { BudgetsCard(state.budgets, onOpen = actions.onOpenBudgets) }
                item { TopCategoriesCard(state.topCategories, state.expenseTotal) }
                item { RecentCard(state, actions) }
                if (state.goals.isNotEmpty()) {
                    item { GoalsCard(state.goals, onOpen = actions.onOpenGoals) }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(state: DashboardUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                stringResource(R.string.dashboard_total_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Spacing.xs))
            AmountText(
                cents = state.totalBalance,
                style = MaterialTheme.typography.displaySmall,
                color = if (state.totalBalance < 0) MaterialTheme.finance.expense else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            state.period?.let {
                Spacer(Modifier.height(Spacing.l))
                Text(
                    periodLabel(PeriodType.MONTH, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Row(Modifier.fillMaxWidth()) {
                LabeledAmount(
                    label = stringResource(R.string.label_income),
                    cents = state.totals?.income ?: 0,
                    type = TransactionType.INCOME,
                    modifier = Modifier.weight(1f),
                )
                LabeledAmount(
                    label = stringResource(R.string.label_expenses),
                    cents = state.totals?.expense ?: 0,
                    type = TransactionType.EXPENSE,
                    modifier = Modifier.weight(1f),
                )
                LabeledAmount(
                    label = stringResource(R.string.label_net),
                    cents = state.totals?.net ?: 0,
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }
        }
    }
}

@Composable
private fun OverBudgetBanner(over: List<BudgetProgress>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.finance.expenseContainer),
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.finance.expense)
            Text(
                text = pluralStringResource(
                    R.plurals.dashboard_over_budget,
                    over.size,
                    over.size,
                    over.joinToString { it.category.name },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
    }
}

@Composable
private fun BudgetsCard(budgets: List<BudgetProgress>, onOpen: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    SectionCard(
        title = stringResource(R.string.budgets_title),
        action = { TextButton(onClick = onOpen) { Text(stringResource(R.string.action_all)) } },
    ) {
        if (budgets.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_no_budgets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.budget_add))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                budgets.take(4).forEach { budget ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                budget.category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${formatter.format(budget.spent)} / ${formatter.format(budget.budget.amount)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (budget.isOver) MaterialTheme.finance.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        BudgetBar(budget.ratio)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCategoriesCard(categories: List<CategoryTotal>, total: Long) {
    val formatter = LocalMoneyFormatter.current
    ExpandableCard(
        title = stringResource(R.string.dashboard_top_categories),
        summary = {
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_no_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                categories.take(3).forEach { CategoryShareRow(it, total, formatter::format) }
            }
        },
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                categories.drop(3).forEach { CategoryShareRow(it, total, formatter::format) }
                if (categories.size <= 3) {
                    Text(
                        stringResource(R.string.dashboard_no_more_categories),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun CategoryShareRow(item: CategoryTotal, total: Long, format: (Long, Boolean) -> String) {
    val share = if (total > 0) item.total.toFloat() / total else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColoredIcon(item.category.icon, item.category.color, size = 36.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m),
        ) {
            Row {
                Text(
                    item.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${(share * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.xs))
            BudgetBar(share, color = Color(item.category.color), height = 6.dp)
        }
        Text(format(item.total, false), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RecentCard(state: DashboardUiState, actions: DashboardActions) {
    SectionCard(
        title = stringResource(R.string.dashboard_recent),
        action = { TextButton(onClick = actions.onSeeAllTransactions) { Text(stringResource(R.string.action_all)) } },
    ) {
        if (state.recent.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = stringResource(R.string.transactions_empty_title),
                message = stringResource(R.string.transactions_empty_message),
                action = { TextButton(onClick = actions.onAddTransaction) { Text(stringResource(R.string.action_add_transaction)) } },
            )
        } else {
            Column {
                state.recent.forEach {
                    TransactionRow(it, onClick = { actions.onOpenTransaction(it.transaction.id) }, horizontalPadding = 0.dp)
                }
            }
        }
    }
}

@Composable
private fun GoalsCard(goals: List<SavingsGoal>, onOpen: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    SectionCard(
        title = stringResource(R.string.goals_title),
        action = { TextButton(onClick = onOpen) { Text(stringResource(R.string.action_all)) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            goals.forEach { goal ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColoredIcon(goal.icon, goal.color, size = 36.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.m),
                    ) {
                        Row {
                            Text(goal.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${formatter.format(goal.saved)} / ${formatter.format(goal.target)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        BudgetBar(goal.progress, color = Color(goal.color))
                    }
                }
            }
        }
    }
}

private val previewState = DashboardUiState(
    loading = false,
    totalBalance = 769_962,
    period = DateRange(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)),
    totals = PreviewData.monthTotals,
    topCategories = PreviewData.categoryTotals,
    recent = PreviewData.transactions,
    budgets = PreviewData.budgets,
    goals = PreviewData.goals.take(1),
)

@Preview(name = "Phone", device = Devices.PIXEL_7)
@Composable
private fun DashboardPhonePreview() {
    TgFinanceTheme { DashboardScreen(previewState, refreshing = false, actions = DashboardActions()) }
}

@Preview(name = "Tablet", device = Devices.PIXEL_TABLET)
@Composable
private fun DashboardTabletPreview() {
    TgFinanceTheme { DashboardScreen(previewState, refreshing = false, actions = DashboardActions()) }
}

@Preview(name = "Empty", device = Devices.PIXEL_7)
@Composable
private fun DashboardEmptyPreview() {
    TgFinanceTheme { DashboardScreen(DashboardUiState(loading = false), refreshing = false, actions = DashboardActions()) }
}
