package com.tgfinance.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.tgfinance.app.ui.components.ColorPicker
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.IconPicker
import com.tgfinance.app.ui.components.OptionalDateField
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.LocalMoneyFormatter
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.DateFormats
import java.time.LocalDate

@Composable
fun GoalsRoute(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalsScreen(state, onBack, onEdit, viewModel::adjust)
}

private data class AdjustDialog(val goalId: String, val deposit: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onAdjust: (String, String, Boolean) -> Boolean,
) {
    var dialog by remember { mutableStateOf<AdjustDialog?>(null) }
    val formatter = LocalMoneyFormatter.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.goal_add)) }
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
            if (state.goals.isEmpty() && !state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.Savings,
                        title = stringResource(R.string.goals_empty_title),
                        message = stringResource(R.string.goals_empty_message),
                        action = { TextButton(onClick = { onEdit(null) }) { Text(stringResource(R.string.goal_add)) } },
                    )
                }
            } else if (state.goals.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.goals_total, formatter.format(state.totalSaved), formatter.format(state.totalTarget)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = Spacing.s),
                    )
                }
            }
            items(state.goals, key = { it.goal.id }) { item ->
                GoalCard(
                    item = item,
                    onEdit = { onEdit(item.goal.id) },
                    onDeposit = { dialog = AdjustDialog(item.goal.id, true) },
                    onWithdraw = { dialog = AdjustDialog(item.goal.id, false) },
                )
            }
        }
    }
    dialog?.let { current ->
        var amount by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (current.deposit) R.string.goal_deposit else R.string.goal_withdraw)) },
            text = {
                AmountField(value = amount, onValueChange = {
                    amount = it
                    error = false
                }, isError = error)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onAdjust(current.goalId, amount, current.deposit)) dialog = null else error = true
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun GoalCard(item: GoalItem, onEdit: () -> Unit, onDeposit: () -> Unit, onWithdraw: () -> Unit) {
    val goal = item.goal
    val formatter = LocalMoneyFormatter.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColoredIcon(goal.icon, goal.color, size = 44.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.m),
                ) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    goal.targetDate?.let {
                        Text(
                            stringResource(R.string.goal_until, it.format(DateFormats.medium)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
            }
            Spacer(Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatter.format(goal.saved), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.goal_of, formatter.format(goal.target)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            BudgetBar(goal.progress, color = Color(goal.color), height = 10.dp)
            Spacer(Modifier.height(Spacing.s))
            if (goal.isReached) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.finance.income)
                    Text(
                        stringResource(R.string.goal_reached),
                        color = MaterialTheme.finance.income,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            } else {
                Text(
                    text = item.monthlyNeeded?.let { stringResource(R.string.goal_monthly_needed, formatter.format(it)) }
                        ?: stringResource(R.string.goal_missing, formatter.format(goal.target - goal.saved)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilledTonalButton(onClick = onDeposit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_deposit)) }
                OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f), enabled = goal.saved > 0) {
                    Text(stringResource(R.string.goal_withdraw))
                }
            }
        }
    }
}

@Composable
fun GoalEditRoute(id: String?, onDone: () -> Unit, viewModel: GoalEditViewModel = hiltViewModel()) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    GoalEditScreen(state, viewModel::update, viewModel::save, viewModel::delete, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    state: GoalEditUiState,
    onChange: ((GoalEditUiState) -> GoalEditUiState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.goal_add else R.string.goal_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(R.string.action_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColoredIcon(state.icon, state.color, size = 56.dp)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { name -> onChange { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                )
            }
            AmountField(
                value = state.targetText,
                onValueChange = { text -> onChange { it.copy(targetText = text) } },
                label = stringResource(R.string.field_target_amount),
            )
            AmountField(
                value = state.savedText,
                onValueChange = { text -> onChange { it.copy(savedText = text) } },
                label = stringResource(R.string.field_saved_amount),
            )
            OptionalDateField(
                label = stringResource(R.string.field_target_date),
                date = state.targetDate,
                onDateChange = { date -> onChange { it.copy(targetDate = date) } },
            )
            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
            ColorPicker(selected = state.color, onSelect = { color -> onChange { it.copy(color = color) } })
            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
            IconPicker(selected = state.icon, color = Color(state.color), onSelect = { icon -> onChange { it.copy(icon = icon) } })
            state.error?.let { Text(stringResource(it), color = MaterialTheme.finance.expense) }
            Button(
                onClick = onSave,
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
            title = stringResource(R.string.goal_delete_title),
            message = stringResource(R.string.goal_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun GoalsPreview() {
    TgFinanceTheme {
        GoalsScreen(
            GoalsUiState(false, PreviewData.goals.map { GoalItem(it, GoalsViewModel.monthlyNeeded(it, LocalDate.of(2024, 5, 15))) }),
            onBack = {},
            onEdit = {},
            onAdjust = { _, _, _ -> true },
        )
    }
}

@Preview(device = Devices.PIXEL_7, heightDp = 1200)
@Composable
private fun GoalEditPreview() {
    TgFinanceTheme { GoalEditScreen(GoalEditUiState(name = "Urlaub", targetText = "3000", icon = "flight"), {}, {}, {}, {}) }
}
