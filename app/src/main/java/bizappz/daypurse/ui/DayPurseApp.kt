package bizappz.daypurse.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.DayPurseApplication
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import bizappz.daypurse.ui.navigation.AppActions
import bizappz.daypurse.ui.navigation.RootNavHost
import bizappz.daypurse.ui.navigation.TabNavHost
import bizappz.daypurse.ui.navigation.TopLevelDestination

@Composable
fun DayPurseApp() = RootNavHost()

/** The four tabs with the bottom bar. Stays composed underneath full-screen pages. */
@Composable
fun MainTabs(actions: AppActions) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Launch actions (shortcut, widget, tile, notification) are handled on Home.
    val container = (LocalContext.current.applicationContext as DayPurseApplication).container
    val launch by container.launchActions.collectAsStateWithLifecycle()
    LaunchedEffect(launch) {
        if (launch != null && currentDestination?.route != TopLevelDestination.HOME.route) {
            navController.navigate(TopLevelDestination.HOME.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(painterResource(if (selected) dest.selectedIcon else dest.icon), contentDescription = null)
                        },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        TabNavHost(navController, actions, Modifier.padding(innerPadding))
    }
}
