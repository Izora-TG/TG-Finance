package com.tgfinance.app.ui.accounts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.AmountField
import com.tgfinance.app.ui.components.ColorPicker
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.IconPicker
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.app.util.accountTypeLabel
import com.tgfinance.core.model.AccountType

@Composable
fun AccountEditRoute(
    id: String?,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    AccountEditScreen(
        state = state,
        onChange = viewModel::update,
        onTypeChange = viewModel::setType,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onClose = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(
    state: AccountEditUiState,
    onChange: ((AccountForm) -> AccountForm) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val form = state.form
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.account_add else R.string.account_edit)) },
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
                ColoredIcon(form.icon, form.color, size = 56.dp)
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { name -> onChange { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.m),
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Text(stringResource(R.string.field_account_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AccountType.entries.forEach { type ->
                    FilterChip(selected = form.type == type, onClick = { onTypeChange(type) }, label = { Text(accountTypeLabel(type)) })
                }
            }

            AmountField(
                value = form.initialBalanceText,
                onValueChange = { text -> onChange { it.copy(initialBalanceText = text) } },
                label = stringResource(R.string.field_initial_balance),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_negative_balance), modifier = Modifier.weight(1f))
                Switch(checked = form.negativeInitial, onCheckedChange = { value -> onChange { it.copy(negativeInitial = value) } })
            }

            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
            ColorPicker(selected = form.color, onSelect = { color -> onChange { it.copy(color = color) } })

            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
            IconPicker(selected = form.icon, color = Color(form.color), onSelect = { icon -> onChange { it.copy(icon = icon) } })

            if (!state.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_archived))
                        Text(
                            stringResource(R.string.field_archived_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.archived, onCheckedChange = { value -> onChange { it.copy(archived = value) } })
                }
            }

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
            title = stringResource(R.string.account_delete_title),
            message = stringResource(R.string.account_delete_message),
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
private fun AccountEditPreview() {
    TgFinanceTheme {
        AccountEditScreen(
            state = AccountEditUiState(form = AccountForm(id = "a1", name = "Girokonto", initialBalanceText = "1500,00")),
            onChange = {},
            onTypeChange = {},
            onSave = {},
            onDelete = {},
            onClose = {},
        )
    }
}
