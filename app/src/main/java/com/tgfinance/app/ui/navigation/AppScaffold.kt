package com.tgfinance.app.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tgfinance.app.R

/** Layout information the screens need to adapt (two panes, FAB placement). */
data class AdaptiveInfo(
    /** Navigation rail instead of a bottom bar (tablets, unfolded foldables, landscape). */
    val useRail: Boolean,
    /** Enough width for list + detail side by side. */
    val twoPane: Boolean,
)

val LocalAdaptiveInfo = staticCompositionLocalOf { AdaptiveInfo(useRail = false, twoPane = false) }

fun WindowSizeClass.toAdaptiveInfo() = AdaptiveInfo(
    useRail = widthSizeClass != WindowWidthSizeClass.Compact,
    twoPane = widthSizeClass == WindowWidthSizeClass.Expanded,
)

@Composable
fun AppScaffold(
    adaptiveInfo: AdaptiveInfo,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isTopLevel = TopLevelDestination.entries.any { destination.isOn(it) }

    val onSelect: (TopLevelDestination) -> Unit = { target ->
        navController.navigate(target.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val onAdd = { navController.navigate(Dest.TransactionEdit()) }

    CompositionLocalProvider(LocalAdaptiveInfo provides adaptiveInfo) {
        if (adaptiveInfo.useRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    header = {
                        FloatingActionButton(
                            onClick = onAdd,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_transaction))
                        }
                    },
                ) {
                    TopLevelDestination.entries.forEach { item ->
                        val selected = destination.isOn(item)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelect(item) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                AppNavHost(
                    navController = navController,
                    modifier = Modifier
                        .weight(1f)
                        .consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)),
                )
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            TopLevelDestination.entries.forEach { item ->
                                val selected = destination.isOn(item)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onSelect(item) },
                                    icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                                    label = { Text(stringResource(item.label), maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                )
            }
        }
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.route::class) } == true
