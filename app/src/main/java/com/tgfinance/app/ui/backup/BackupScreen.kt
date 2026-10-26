package com.tgfinance.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.components.ConfirmDialog
import com.tgfinance.app.ui.components.SectionCard
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import java.time.LocalDate

@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    BackupScreen(
        state = state,
        onBack = onBack,
        onExportCsv = { csvLauncher.launch("tg-finance-buchungen-$today.csv") },
        onExportBackup = { backupLauncher.launch("tg-finance-backup-$today.json") },
        onRestore = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        onMessageShown = viewModel::clearMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(context.getString(it.text, it.count))
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
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
                BackupCard(
                    icon = Icons.Outlined.TableChart,
                    title = stringResource(R.string.backup_csv_title),
                    message = stringResource(R.string.backup_csv_message),
                ) {
                    FilledTonalButton(onClick = onExportCsv, enabled = !state.busy) { Text(stringResource(R.string.backup_csv_action)) }
                }
                BackupCard(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.backup_export_title),
                    message = stringResource(R.string.backup_export_message),
                ) {
                    Button(onClick = onExportBackup, enabled = !state.busy) { Text(stringResource(R.string.backup_export_action)) }
                }
                BackupCard(
                    icon = Icons.Outlined.Restore,
                    title = stringResource(R.string.backup_import_title),
                    message = stringResource(R.string.backup_import_message),
                ) {
                    OutlinedButton(onClick = { confirmRestore = true }, enabled = !state.busy) {
                        Text(stringResource(R.string.backup_import_action))
                    }
                }
            }
        }
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            message = stringResource(R.string.backup_import_confirm_message),
            confirmLabel = stringResource(R.string.backup_import_action),
            onConfirm = {
                confirmRestore = false
                onRestore()
            },
            onDismiss = { confirmRestore = false },
        )
    }
}

@Composable
private fun BackupCard(icon: ImageVector, title: String, message: String, action: @Composable () -> Unit) {
    SectionCard {
        ColoredIcon(icon, MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.m))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.s),
        )
        action()
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun BackupPreview() {
    TgFinanceTheme { BackupScreen(BackupUiState(), {}, {}, {}, {}, {}) }
}
