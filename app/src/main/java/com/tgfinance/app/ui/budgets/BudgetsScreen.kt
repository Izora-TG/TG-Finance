package com.tgfinance.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tgfinance.app.ui.components.AmountField
import com.tgfinance.app.ui.components.BudgetBar
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.periodLabel
import com.tgfinance.core.model.BudgetProgress
import com.tgfinance.core.model.Category
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodType
import java.time.LocalDate

@Composable
fun BudgetsRoute(onBack: () -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetsScreen(
        state = state,
        onBack = onBack,
        onPrevious = viewModel::previousPeriod,
        onNext = viewModel::nextPeriod,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** Budget being edited in the dialog; [budget] is null when creating one. */
private data class BudgetDialog(val budget: BudgetProgress?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: (String?, String, String) -> Boolean,
    onDelete: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf<BudgetDialog?>(null) }
    val formatter = LocalMoneyFormatter.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.availableCategories.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { dialog = BudgetDialog(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.budget_add)) }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.action_previous_period))
                    }
                    Text(
                        state.period?.let { periodLabel(PeriodType.MONTH, it) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = onNext, enabled = !state.isCurrentPeriod) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.action_next_period))
                    }
                }
            }
            if (state.budgets.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(title = stringResource(R.string.budgets_total)) {
                        Row {
                            Text(
                                stringResource(R.string.budgets_spent_of, formatter.format(state.totalSpent), formatter.format(state.totalBudget)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(state.totalRatio * 100).toInt()} %", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(Spacing.s))
                        BudgetBar(state.totalRatio, height = 10.dp)
                    }
                }
            } else if (!state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.Savings,
                        title = stringResource(R.string.budgets_empty_title),
                        message = stringResource(R.string.budgets_empty_message),
                        action = { TextButton(onClick = { dialog = BudgetDialog(null) }) { Text(stringResource(R.string.budget_add)) } },
                    )
                }
            }
            items(state.budgets, key = { it.budget.id }) { budget ->
                BudgetCard(budget, onClick = { dialog = BudgetDialog(budget) })
            }
        }
    }

    dialog?.let { current ->
        BudgetEditDialog(
            budget = current.budget,
            categories = state.availableCategories,
            onDismiss = { dialog = null },
            onSave = { categoryId, amount ->
                if (onSave(current.budget?.budget?.id, categoryId, amount)) dialog = null
            },
            onDelete = {
                current.budget?.let { onDelete(it.budget.id) }
                dialog = null
            },
        )
    }
}

@Composable
private fun BudgetCard(budget: BudgetProgress, onClick: () -> Unit) {
    val formatter = LocalMoneyFormatter.current
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColoredIcon(budget.category.icon, budget.category.color)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.m),
                ) {
                    Text(budget.category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.budgets_spent_of, formatter.format(budget.spent), formatter.format(budget.budget.amount)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (budget.isOver || budget.isWarning) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = stringResource(if (budget.isOver) R.string.budget_exceeded else R.string.budget_warning),
                        tint = if (budget.isOver) MaterialTheme.finance.expense else MaterialTheme.finance.warning,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
            BudgetBar(budget.ratio, height = 10.dp)
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = if (budget.isOver) {
                    stringResource(R.string.budget_over_by, formatter.format(-budget.remaining))
                } else {
                    stringResource(R.string.budget_remaining, formatter.format(budget.remaining))
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    budget.isOver -> MaterialTheme.finance.expense
                    budget.isWarning -> MaterialTheme.finance.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditDialog(
    budget: BudgetProgress?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    var categoryId by rememberSaveable { mutableStateOf(budget?.category?.id ?: categories.firstOrNull()?.id) }
    var amount by rememberSaveable { mutableStateOf(budget?.let { formatter.formatPlain(it.budget.amount) } ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val options = if (budget != null) listOf(budget.category) else categories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (budget == null) R.string.budget_add else R.string.budget_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = budget == null && it }) {
                    OutlinedTextField(
                        value = options.firstOrNull { it.id == categoryId }?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                leadingIcon = { ColoredIcon(category.icon, category.color, size = 28.dp) },
                                onClick = {
                                    categoryId = category.id
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                AmountField(value = amount, onValueChange = { amount = it }, label = stringResource(R.string.field_monthly_limit))
                Text(
                    stringResource(R.string.budget_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { categoryId?.let { onSave(it, amount) } }, enabled = categoryId != null && amount.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (budget != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.finance.expense)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.budget_delete_title),
            message = stringResource(R.string.budget_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun BudgetsPreview() {
    TgFinanceTheme {
        BudgetsScreen(
            state = BudgetsUiState(
                loading = false,
                period = DateRange(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31)),
                budgets = PreviewData.budgets,
                availableCategories = listOf(PreviewData.housing),
            ),
            onBack = {}, onPrevious = {}, onNext = {}, onSave = { _, _, _ -> true }, onDelete = {},
        )
    }
}

@Preview(device = Devices.PIXEL_TABLET)
@Composable
private fun BudgetsTabletPreview() {
    TgFinanceTheme {
        Box {
            BudgetsScreen(
                state = BudgetsUiState(loading = false, budgets = PreviewData.budgets),
                onBack = {}, onPrevious = {}, onNext = {}, onSave = { _, _, _ -> true }, onDelete = {},
            )
        }
    }
}
