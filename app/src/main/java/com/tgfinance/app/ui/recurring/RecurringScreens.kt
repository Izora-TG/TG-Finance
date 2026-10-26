package com.tgfinance.app.ui.recurring

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountField
import com.tgfinance.app.ui.components.AmountText
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.DatePickerDialogWrapper
import com.tgfinance.app.ui.components.EmptyState
import com.tgfinance.app.ui.components.FinanceIcons
import com.tgfinance.app.ui.components.OptionalDateField
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.preview.PreviewData
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.ui.transactions.typeLabel
import com.tgfinance.app.util.DateFormats
import com.tgfinance.app.util.frequencyLabel
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.model.TransactionType

@Composable
fun RecurringRoute(onBack: () -> Unit, onEdit: (String?) -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringScreen(state, onBack, onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(state: RecurringUiState, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
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
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recurring_add)) }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (state.items.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Autorenew,
                        title = stringResource(R.string.recurring_empty_title),
                        message = stringResource(R.string.recurring_empty_message),
                        action = { TextButton(onClick = { onEdit(null) }) { Text(stringResource(R.string.recurring_add)) } },
                    )
                }
            } else if (state.items.isNotEmpty()) {
                item {
                    SectionCard(modifier = Modifier.padding(Spacing.l)) {
                        Text(
                            stringResource(R.string.recurring_monthly_effect),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AmountText(
                            state.monthlyNet,
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (state.monthlyNet < 0) MaterialTheme.finance.expense else MaterialTheme.finance.income,
                        )
                    }
                }
            }
            items(state.items, key = { it.rule.id }) { item ->
                val rule = item.rule
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(rule.id) }
                        .alpha(if (rule.active) 1f else 0.5f)
                        .padding(horizontal = Spacing.l, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (rule.type == TransactionType.TRANSFER) {
                        ColoredIcon(Icons.Outlined.SwapHoriz, MaterialTheme.colorScheme.secondary)
                    } else {
                        ColoredIcon(
                            FinanceIcons.forKey(item.category?.icon ?: "more"),
                            item.category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.m),
                    ) {
                        Text(
                            rule.note.ifBlank { item.category?.name ?: typeLabel(rule.type) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (rule.active) {
                                "${frequencyLabel(rule.frequency, rule.interval)} · ${stringResource(R.string.recurring_next, rule.nextDate.format(DateFormats.medium))}"
                            } else {
                                stringResource(R.string.recurring_paused)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AmountText(rule.amount, type = rule.type, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun RecurringEditRoute(id: String?, onDone: () -> Unit, viewModel: RecurringEditViewModel = hiltViewModel()) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    RecurringEditScreen(state, viewModel::update, viewModel::setType, viewModel::save, viewModel::delete, onDone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(
    state: RecurringEditUiState,
    onChange: ((RecurringForm) -> RecurringForm) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val form = state.form
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.recurring_add else R.string.recurring_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
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
            val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) { Text(typeLabel(type)) }
                }
            }
            AmountField(value = form.amountText, onValueChange = { text -> onChange { it.copy(amountText = text) } })
            OutlinedTextField(
                value = form.note,
                onValueChange = { text -> onChange { it.copy(note = text) } },
                label = { Text(stringResource(R.string.field_name_hint_recurring)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            if (form.type != TransactionType.TRANSFER) {
                Label(stringResource(R.string.field_category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = form.categoryId == category.id,
                            onClick = { onChange { it.copy(categoryId = category.id) } },
                            label = { Text(category.name) },
                            leadingIcon = { Icon(FinanceIcons.forKey(category.icon), null, tint = Color(category.color)) },
                        )
                    }
                }
            }
            Label(stringResource(if (form.type == TransactionType.TRANSFER) R.string.field_from_account else R.string.field_account))
            ChipRow {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = form.accountId == account.id,
                        onClick = { onChange { it.copy(accountId = account.id) } },
                        label = { Text(account.name) },
                    )
                }
            }
            if (form.type == TransactionType.TRANSFER) {
                Label(stringResource(R.string.field_to_account))
                ChipRow {
                    state.accounts.filter { it.id != form.accountId }.forEach { account ->
                        FilterChip(
                            selected = form.toAccountId == account.id,
                            onClick = { onChange { it.copy(toAccountId = account.id) } },
                            label = { Text(account.name) },
                        )
                    }
                }
            }

            Label(stringResource(R.string.field_frequency))
            ChipRow {
                Frequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = form.frequency == frequency,
                        onClick = { onChange { it.copy(frequency = frequency) } },
                        label = { Text(frequencyLabel(frequency, 1)) },
                    )
                }
            }
            OutlinedTextField(
                value = form.interval.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let { value -> onChange { it.copy(interval = value.coerceIn(1, 365)) } } },
                label = { Text(stringResource(R.string.field_interval)) },
                supportingText = { Text(frequencyLabel(form.frequency, form.interval)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(200.dp),
                shape = MaterialTheme.shapes.medium,
            )

            Label(stringResource(R.string.field_start_date))
            TextButton(onClick = { pickStart = true }) { Text(form.startDate.format(DateFormats.medium)) }
            OptionalDateField(
                label = stringResource(R.string.field_end_date),
                date = form.endDate,
                onDateChange = { date -> onChange { it.copy(endDate = date) } },
            )
            if (!state.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_active), modifier = Modifier.weight(1f))
                    Switch(checked = form.active, onCheckedChange = { value -> onChange { it.copy(active = value) } })
                }
            }
            Text(
                stringResource(R.string.recurring_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Text(stringResource(it), color = MaterialTheme.finance.expense) }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.height(Spacing.l))
        }
    }
    if (pickStart) {
        DatePickerDialogWrapper(
            initial = form.startDate,
            onDismiss = { pickStart = false },
            onConfirm = { date ->
                onChange { it.copy(startDate = date) }
                pickStart = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.recurring_delete_title),
            message = stringResource(R.string.recurring_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun RecurringPreview() {
    TgFinanceTheme {
        RecurringScreen(
            RecurringUiState(
                loading = false,
                items = PreviewData.recurring.map { rule ->
                    RecurringItem(rule, PreviewData.categories.firstOrNull { it.id == rule.categoryId }, PreviewData.checking)
                },
            ),
            onBack = {},
            onEdit = {},
        )
    }
}

@Preview(device = Devices.PIXEL_7, heightDp = 1200)
@Composable
private fun RecurringEditPreview() {
    TgFinanceTheme {
        RecurringEditScreen(
            RecurringEditUiState(
                form = RecurringForm(amountText = "950,00", categoryId = "c2", accountId = "a1", note = "Miete"),
                categories = PreviewData.categories,
                accounts = PreviewData.accounts.map { it.account },
            ),
            {}, {}, {}, {}, {},
        )
    }
}
