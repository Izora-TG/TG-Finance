package com.tgfinance.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.tgfinance.app.R
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
object Dest {
    @Serializable data object Dashboard
    @Serializable data object Transactions
    @Serializable data object Accounts
    @Serializable data object Statistics
    @Serializable data object More

    /** [type] preselects INCOME/EXPENSE/TRANSFER for new bookings. */
    @Serializable data class TransactionEdit(val id: String? = null, val type: String? = null, val accountId: String? = null)
    @Serializable data class AccountDetail(val id: String)
    @Serializable data class AccountEdit(val id: String? = null)
    @Serializable data object Categories
    @Serializable data class CategoryEdit(val id: String? = null, val type: String = "EXPENSE")
    @Serializable data object Budgets
    @Serializable data object Recurring
    @Serializable data class RecurringEdit(val id: String? = null)
    @Serializable data object Goals
    @Serializable data class GoalEdit(val id: String? = null)
    @Serializable data object Settings
    @Serializable data object Backup
    @Serializable data object Sync
}

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    DASHBOARD(Dest.Dashboard, R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home),
    TRANSACTIONS(Dest.Transactions, R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ACCOUNTS(Dest.Accounts, R.string.nav_accounts, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    STATISTICS(Dest.Statistics, R.string.nav_statistics, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    MORE(Dest.More, R.string.nav_more, Icons.Filled.GridView, Icons.Outlined.GridView),
}
