package com.tgfinance.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.data.local.entity.SyncConflictEntity
import com.tgfinance.app.data.sync.SyncStatus
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.ExpandableCard
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import com.tgfinance.app.ui.theme.finance
import com.tgfinance.core.sync.SyncException

data class SyncActions(
    val onBack: () -> Unit = {},
    val onFormChange: ((SyncForm) -> SyncForm) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onRegister: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onEnableEncryption: (String) -> Unit = {},
    val onUnlockEncryption: (String) -> Unit = {},
    val onDisableEncryption: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun SyncRoute(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncScreen(
        state,
        SyncActions(
            onBack = onBack,
            onFormChange = viewModel::updateForm,
            onSignIn = { viewModel.signIn(register = false) },
            onRegister = { viewModel.signIn(register = true) },
            onSyncNow = viewModel::syncNow,
            onSignOut = viewModel::signOut,
            onEnableEncryption = viewModel::enableEncryption,
            onUnlockEncryption = viewModel::unlockEncryption,
            onDisableEncryption = viewModel::disableEncryption,
            onMessageShown = viewModel::messageShown,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(state: SyncUiState, actions: SyncActions) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.getString(it))
            actions.onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.status.enabled) {
                    ConnectedContent(state, actions)
                } else {
                    SignInContent(state, actions)
                }
            }
        }
    }
}

@Composable
private fun SignInContent(state: SyncUiState, actions: SyncActions) {
    SectionCard {
        ColoredIcon(Icons.Outlined.CloudSync, MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.sync_intro_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.m))
        Text(
            stringResource(R.string.sync_intro_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
    val form = state.form
    OutlinedTextField(
        value = form.serverUrl,
        onValueChange = { v -> actions.onFormChange { it.copy(serverUrl = v) } },
        label = { Text(stringResource(R.string.sync_server_url)) },
        placeholder = { Text("https://finance.example.com") },
        supportingText = { Text(stringResource(R.string.sync_server_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.username,
        onValueChange = { v -> actions.onFormChange { it.copy(username = v) } },
        label = { Text(stringResource(R.string.sync_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.password,
        onValueChange = { v -> actions.onFormChange { it.copy(password = v) } },
        label = { Text(stringResource(R.string.sync_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    val canSubmit = !state.busy && form.serverUrl.isNotBlank() && form.username.isNotBlank() && form.password.isNotBlank()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Button(onClick = actions.onSignIn, enabled = canSubmit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_sign_in)) }
        OutlinedButton(onClick = actions.onRegister, enabled = canSubmit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_register)) }
    }
}

@Composable
private fun ConnectedContent(state: SyncUiState, actions: SyncActions) {
    val status = state.status
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var passwordDialog by rememberSaveable { mutableStateOf<String?>(null) } // "enable" | "unlock"
    var confirmDisable by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.sync_status_title)) {
        Text(syncStatusText(status), style = MaterialTheme.typography.titleMedium)
        status.error?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.finance.warning, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.sync_connected_as, status.username, status.serverUrl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.s),
        )
        FilledTonalButton(onClick = actions.onSyncNow, enabled = !state.busy && !status.running) {
            Text(stringResource(R.string.sync_now))
        }
    }

    SectionCard(title = stringResource(R.string.sync_e2e_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.sync_e2e_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.m),
            )
            Switch(
                checked = status.encrypted,
                enabled = !state.busy,
                onCheckedChange = { on -> if (on) passwordDialog = "enable" else confirmDisable = true },
            )
        }
        if (status.error == SyncException.Kind.ENCRYPTION || !status.encrypted) {
            TextButton(onClick = { passwordDialog = "unlock" }) { Text(stringResource(R.string.sync_e2e_unlock)) }
        }
    }

    if (state.conflicts.isNotEmpty()) {
        ExpandableCard(
            title = stringResource(R.string.sync_conflicts_title, state.conflicts.size),
            summary = {
                Text(
                    stringResource(R.string.sync_conflicts_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            state.conflicts.take(50).forEach { ConflictRow(it) }
        }
    }

    TextButton(onClick = { confirmSignOut = true }) {
        Text(stringResource(R.string.sync_sign_out), color = MaterialTheme.finance.expense)
    }

    passwordDialog?.let { mode ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordDialog = null },
            title = { Text(stringResource(if (mode == "enable") R.string.sync_e2e_enable_title else R.string.sync_e2e_unlock)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(if (mode == "enable") R.string.sync_e2e_enable_message else R.string.sync_e2e_unlock_message))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.sync_e2e_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= 8, onClick = {
                    if (mode == "enable") actions.onEnableEncryption(password) else actions.onUnlockEncryption(password)
                    passwordDialog = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { passwordDialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDisable) {
        ConfirmDialog(
            title = stringResource(R.string.sync_e2e_disable_title),
            message = stringResource(R.string.sync_e2e_disable_message),
            confirmLabel = stringResource(R.string.sync_e2e_disable_action),
            onConfirm = {
                confirmDisable = false
                actions.onDisableEncryption()
            },
            onDismiss = { confirmDisable = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.sync_sign_out_title),
            message = stringResource(R.string.sync_sign_out_message),
            confirmLabel = stringResource(R.string.sync_sign_out),
            onConfirm = {
                confirmSignOut = false
                actions.onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun ConflictRow(conflict: SyncConflictEntity) {
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text(
            stringResource(
                if (conflict.winner == "LOCAL") R.string.sync_conflict_local_won else R.string.sync_conflict_remote_won,
                conflict.entityType,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatTime(conflict.detectedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun SyncSignInPreview() {
    TgFinanceTheme { SyncScreen(SyncUiState(), SyncActions()) }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun SyncConnectedPreview() {
    TgFinanceTheme {
        SyncScreen(
            SyncUiState(
                status = SyncStatus(enabled = true, lastSyncAt = 1_715_760_000_000, serverUrl = "https://finance.example.com", username = "alex", encrypted = true),
                conflicts = listOf(SyncConflictEntity(1, "transaction", "t1", 1, 2, "a", "b", "REMOTE", 1_715_760_000_000)),
            ),
            SyncActions(),
        )
    }
}
