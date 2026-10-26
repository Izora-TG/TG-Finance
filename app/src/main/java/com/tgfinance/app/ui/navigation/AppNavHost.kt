package com.tgfinance.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tgfinance.app.ui.accounts.AccountDetailRoute
import com.tgfinance.app.ui.accounts.AccountEditRoute
import com.tgfinance.app.ui.accounts.AccountsRoute
import com.tgfinance.app.ui.backup.BackupRoute
import com.tgfinance.app.ui.budgets.BudgetsRoute
import com.tgfinance.app.ui.categories.CategoriesRoute
import com.tgfinance.app.ui.categories.CategoryEditRoute
import com.tgfinance.app.ui.dashboard.DashboardRoute
import com.tgfinance.app.ui.goals.GoalEditRoute
import com.tgfinance.app.ui.goals.GoalsRoute
import com.tgfinance.app.ui.more.MoreRoute
import com.tgfinance.app.ui.recurring.RecurringEditRoute
import com.tgfinance.app.ui.recurring.RecurringRoute
import com.tgfinance.app.ui.settings.SettingsRoute
import com.tgfinance.app.ui.statistics.StatisticsRoute
import com.tgfinance.app.ui.transactions.TransactionEditRoute
import com.tgfinance.app.ui.transactions.TransactionsRoute
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.TransactionType

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = Dest.Dashboard,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Dest.Dashboard> {
            DashboardRoute(
                onAddTransaction = { navController.navigate(Dest.TransactionEdit()) },
                onOpenTransaction = { navController.navigate(Dest.TransactionEdit(id = it)) },
                onSeeAllTransactions = { navController.navigate(Dest.Transactions) },
                onOpenBudgets = { navController.navigate(Dest.Budgets) },
                onOpenGoals = { navController.navigate(Dest.Goals) },
                onOpenAccounts = { navController.navigate(Dest.Accounts) },
                onOpenSettings = { navController.navigate(Dest.Settings) },
            )
        }
        composable<Dest.Transactions> {
            TransactionsRoute(
                onAdd = { navController.navigate(Dest.TransactionEdit()) },
                onOpen = { navController.navigate(Dest.TransactionEdit(id = it)) },
            )
        }
        composable<Dest.TransactionEdit> { entry ->
            val args = entry.toRoute<Dest.TransactionEdit>()
            TransactionEditRoute(
                id = args.id,
                initialType = args.type?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
                initialAccountId = args.accountId,
                onDone = back,
            )
        }
        composable<Dest.Accounts> {
            AccountsRoute(
                onAddAccount = { navController.navigate(Dest.AccountEdit()) },
                onOpenAccount = { navController.navigate(Dest.AccountDetail(it)) },
                onEditAccount = { navController.navigate(Dest.AccountEdit(it)) },
                onTransfer = { navController.navigate(Dest.TransactionEdit(type = TransactionType.TRANSFER.name)) },
                onOpenTransaction = { navController.navigate(Dest.TransactionEdit(id = it)) },
                onAddTransaction = { accountId -> navController.navigate(Dest.TransactionEdit(accountId = accountId)) },
            )
        }
        composable<Dest.AccountDetail> { entry ->
            val args = entry.toRoute<Dest.AccountDetail>()
            AccountDetailRoute(
                id = args.id,
                onBack = back,
                onEdit = { navController.navigate(Dest.AccountEdit(args.id)) },
                onOpenTransaction = { navController.navigate(Dest.TransactionEdit(id = it)) },
                onAddTransaction = { navController.navigate(Dest.TransactionEdit(accountId = args.id)) },
            )
        }
        composable<Dest.AccountEdit> { entry ->
            val args = entry.toRoute<Dest.AccountEdit>()
            AccountEditRoute(
                id = args.id,
                onDone = back,
                onDeleted = {
                    // The detail screen of a deleted account makes no sense any more.
                    navController.popBackStack(Dest.Accounts, inclusive = false)
                },
            )
        }
        composable<Dest.Statistics> { StatisticsRoute() }
        composable<Dest.More> {
            MoreRoute(onNavigate = { navController.navigate(it) })
        }
        composable<Dest.Categories> {
            CategoriesRoute(
                onBack = back,
                onEdit = { id, type -> navController.navigate(Dest.CategoryEdit(id, type.name)) },
            )
        }
        composable<Dest.CategoryEdit> { entry ->
            val args = entry.toRoute<Dest.CategoryEdit>()
            CategoryEditRoute(
                id = args.id,
                initialType = runCatching { CategoryType.valueOf(args.type) }.getOrDefault(CategoryType.EXPENSE),
                onDone = back,
            )
        }
        composable<Dest.Budgets> { BudgetsRoute(onBack = back) }
        composable<Dest.Recurring> {
            RecurringRoute(onBack = back, onEdit = { navController.navigate(Dest.RecurringEdit(it)) })
        }
        composable<Dest.RecurringEdit> { entry ->
            RecurringEditRoute(id = entry.toRoute<Dest.RecurringEdit>().id, onDone = back)
        }
        composable<Dest.Goals> {
            GoalsRoute(onBack = back, onEdit = { navController.navigate(Dest.GoalEdit(it)) })
        }
        composable<Dest.GoalEdit> { entry ->
            GoalEditRoute(id = entry.toRoute<Dest.GoalEdit>().id, onDone = back)
        }
        composable<Dest.Settings> {
            SettingsRoute(onBack = back, onNavigate = { navController.navigate(it) })
        }
        composable<Dest.Backup> { BackupRoute(onBack = back) }
    }
}
