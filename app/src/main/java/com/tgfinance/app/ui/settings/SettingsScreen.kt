package com.tgfinance.app.ui.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.BuildConfig
import com.tgfinance.app.R
import com.tgfinance.app.data.settings.AppSettings
import com.tgfinance.app.data.settings.ThemeMode
import com.tgfinance.app.ui.navigation.Dest
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onCurrency: (String) -> Unit = {},
    val onFirstDayOfWeek: (DayOfWeek) -> Unit = {},
    val onMonthStartDay: (Int) -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onBiometricLock: (Boolean) -> Unit = {},
    val onNavigate: (Any) -> Unit = {},
)

@Composable
fun SettingsRoute(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onCurrency = viewModel::setCurrency,
            onFirstDayOfWeek = viewModel::setFirstDayOfWeek,
            onMonthStartDay = viewModel::setMonthStartDay,
            onThemeMode = viewModel::setThemeMode,
            onBiometricLock = viewModel::setBiometricLock,
            onNavigate = onNavigate,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppSettings,
    actions: SettingsActions,
    extraSections: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val biometricAvailable = remember {
        inPreview || BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
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
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionHeader(stringResource(R.string.settings_section_general))
                ChoiceSetting(
                    icon = Icons.Outlined.Euro,
                    title = stringResource(R.string.settings_currency),
                    value = currencyLabel(state.currencyCode),
                    options = SettingsViewModel.currencies.map { currencyLabel(it) to { actions.onCurrency(it) } },
                )
                ChoiceSetting(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.settings_first_day_of_week),
                    value = state.firstDayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).map {
                        it.getDisplayName(TextStyle.FULL, Locale.getDefault()) to { actions.onFirstDayOfWeek(it) }
                    },
                )
                ChoiceSetting(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.settings_month_start),
                    subtitle = stringResource(R.string.settings_month_start_hint),
                    value = stringResource(R.string.settings_month_start_value, state.monthStartDay),
                    options = (1..28).map { day -> "$day." to { actions.onMonthStartDay(day) } },
                )
                ChoiceSetting(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel(state.themeMode),
                    options = ThemeMode.entries.map { mode -> themeLabel(mode) to { actions.onThemeMode(mode) } },
                )

                SectionHeader(stringResource(R.string.settings_section_security))
                SettingRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.settings_biometric),
                    subtitle = stringResource(
                        if (biometricAvailable) R.string.settings_biometric_hint else R.string.settings_biometric_unavailable,
                    ),
                    onClick = if (biometricAvailable) ({ actions.onBiometricLock(!state.biometricLock) }) else null,
                ) {
                    Switch(
                        checked = state.biometricLock,
                        onCheckedChange = actions.onBiometricLock,
                        enabled = biometricAvailable,
                    )
                }

                extraSections()

                SectionHeader(stringResource(R.string.settings_section_data))
                SettingRow(
                    icon = Icons.Outlined.Category,
                    title = stringResource(R.string.categories_title),
                    onClick = { actions.onNavigate(Dest.Categories) },
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                SettingRow(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.backup_title),
                    subtitle = stringResource(R.string.more_backup_subtitle),
                    onClick = { actions.onNavigate(Dest.Backup) },
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }

                HorizontalDivider(Modifier.padding(vertical = Spacing.m))
                SettingRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.s),
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.l),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun ChoiceSetting(
    icon: ImageVector,
    title: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(icon = icon, title = title, subtitle = subtitle, onClick = { open = true }) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.padding(horizontal = Spacing.s)) {
            options.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    action()
                    open = false
                })
            }
        }
    }
}

private fun currencyLabel(code: String): String = runCatching {
    val currency = Currency.getInstance(code)
    "${currency.getDisplayName(Locale.getDefault())} (${currency.getSymbol(Locale.getDefault())})"
}.getOrDefault(code)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.SYSTEM -> R.string.theme_system
    },
)

@Preview(device = Devices.PIXEL_7, heightDp = 1100)
@Composable
private fun SettingsPreview() {
    TgFinanceTheme { SettingsScreen(AppSettings(monthStartDay = 25), SettingsActions()) }
}
