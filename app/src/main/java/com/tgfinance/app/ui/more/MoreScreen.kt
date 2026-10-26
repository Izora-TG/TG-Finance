package com.tgfinance.app.ui.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.tgfinance.app.R
import com.tgfinance.app.ui.components.ColoredIcon
import com.tgfinance.app.ui.navigation.Dest
import com.tgfinance.app.ui.theme.Spacing
import com.tgfinance.app.ui.theme.TgFinanceTheme

data class MoreEntry(
    val route: Any,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
)

val moreEntries = listOf(
    MoreEntry(Dest.Budgets, R.string.budgets_title, R.string.more_budgets_subtitle, Icons.Outlined.PieChart),
    MoreEntry(Dest.Goals, R.string.goals_title, R.string.more_goals_subtitle, Icons.Outlined.Savings),
    MoreEntry(Dest.Recurring, R.string.recurring_title, R.string.more_recurring_subtitle, Icons.Outlined.Autorenew),
    MoreEntry(Dest.Categories, R.string.categories_title, R.string.more_categories_subtitle, Icons.Outlined.Category),
    MoreEntry(Dest.Sync, R.string.sync_title, R.string.more_sync_subtitle, Icons.Outlined.CloudSync),
    MoreEntry(Dest.Backup, R.string.backup_title, R.string.more_backup_subtitle, Icons.Outlined.FileDownload),
    MoreEntry(Dest.Settings, R.string.settings_title, R.string.more_settings_subtitle, Icons.Outlined.Settings),
)

@Composable
fun MoreRoute(onNavigate: (Any) -> Unit, entries: List<MoreEntry> = moreEntries) {
    MoreScreen(entries, onNavigate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(entries: List<MoreEntry>, onNavigate: (Any) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_more)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(entries) { entry ->
                Card(
                    onClick = { onNavigate(entry.route) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ColoredIcon(entry.icon, MaterialTheme.colorScheme.primary)
                        Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(entry.subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.PIXEL_7)
@Composable
private fun MorePreview() {
    TgFinanceTheme { MoreScreen(moreEntries) {} }
}
