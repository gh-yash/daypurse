package bizappz.daypurse.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import bizappz.daypurse.ui.budget.BudgetsScreen
import bizappz.daypurse.ui.budget.BudgetDetailScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bizappz.daypurse.ui.MainTabs
import bizappz.daypurse.DayPurseApplication
import bizappz.daypurse.ui.activity.ActivityScreen
import bizappz.daypurse.ui.payments.PaymentSettingsScreen
import bizappz.daypurse.ui.recurring.RecurringScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bizappz.daypurse.ui.category.CategoriesScreen
import bizappz.daypurse.ui.history.HistoryScreen
import bizappz.daypurse.ui.home.HomeScreen
import bizappz.daypurse.ui.insights.InsightsScreen
import bizappz.daypurse.ui.settings.SettingsScreen
import bizappz.daypurse.ui.setup.BudgetSetupScreen

object Routes {
    const val MAIN = "main"
    const val BUDGET_EDITOR = "budget_editor?id={id}&new={new}"
    const val BUDGET = "budget/{id}"
    const val BUDGETS = "budgets"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"
    const val PAYMENTS = "payments"
    const val ACTIVITY = "activity"

    fun editBudget(id: Long) = "budget_editor?id=$id&new=false"
    fun newBudget() = "budget_editor?id=-1&new=true"
    fun budget(id: Long) = "budget/$id"
}

/** Navigation callbacks shared by the tab screens. */
data class AppActions(
    val editBudget: (Long) -> Unit,
    val newBudget: () -> Unit,
    val openBudget: (Long) -> Unit,
    val openBudgets: () -> Unit,
    val openCategories: () -> Unit,
    val openRecurring: () -> Unit,
    val openPayments: () -> Unit,
    val openActivity: () -> Unit,
)

private const val DURATION = 300

private typealias Enter = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias Exit = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
private typealias PredictiveEnter = AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition
private typealias PredictiveExit = AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition

// Material "shared axis X": the new page slides in from the right while the old one shifts a
// little left and fades; back reverses it. Predictive back drives the pop transition with the
// gesture, so it tracks the finger instead of shrinking the page.
private val pushEnter: Enter = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(DURATION, easing = LinearOutSlowInEasing))
}
private val pushExit: Exit = {
    slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(DURATION / 2))
}
private val popEnter: Enter = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(DURATION, easing = LinearOutSlowInEasing))
}
private val popExit: Exit = {
    // Slide fully off-screen (no fade) so the page visibly leaves instead of vanishing.
    slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { it }
}

// Back gesture (predictive back). The library default shrinks the page to 70% towards the
// centre; instead the page follows the finger off the side it was swiped from, and the page
// underneath slides back in. The gesture scrubs these transitions, so they track the finger.
private val predictivePopEnter: PredictiveEnter = { edge ->
    val fromLeft = edge == BackEventCompat.EDGE_LEFT
    slideInHorizontally(tween(DURATION, easing = LinearEasing)) { if (fromLeft) -it / 4 else it / 4 } +
        fadeIn(tween(DURATION, easing = LinearEasing), initialAlpha = 0.6f)
}
private val predictivePopExit: PredictiveExit = { edge ->
    val fromLeft = edge == BackEventCompat.EDGE_LEFT
    slideOutHorizontally(tween(DURATION, easing = LinearEasing)) { if (fromLeft) it else -it }
}

/** Opaque page background, so a page sliding over another never shows through. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}

/** Root graph: the tabbed main screen plus full-screen pages that slide over it. */
@Composable
fun RootNavHost(navController: NavHostController = rememberNavController()) {
    // A shortcut, widget, tile or notification acts on Home: leave any page that's open.
    val container = (LocalContext.current.applicationContext as DayPurseApplication).container
    val launch by container.launchActions.collectAsStateWithLifecycle()
    LaunchedEffect(launch) {
        if (launch != null && navController.currentDestination?.route != Routes.MAIN) navController.popBackStack(Routes.MAIN, false)
    }
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        enterTransition = pushEnter,
        exitTransition = pushExit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
        predictivePopEnterTransition = predictivePopEnter,
        predictivePopExitTransition = predictivePopExit,
    ) {
        val actions = AppActions(
            editBudget = { navController.navigate(Routes.editBudget(it)) },
            newBudget = { navController.navigate(Routes.newBudget()) },
            openBudget = { navController.navigate(Routes.budget(it)) },
            openBudgets = { navController.navigate(Routes.BUDGETS) },
            openCategories = { navController.navigate(Routes.CATEGORIES) },
            openRecurring = { navController.navigate(Routes.RECURRING) },
            openPayments = { navController.navigate(Routes.PAYMENTS) },
            openActivity = { navController.navigate(Routes.ACTIVITY) },
        )
        composable(Routes.MAIN) { MainTabs(actions) }
        composable(
            Routes.BUDGET_EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("new") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val isNew = entry.arguments?.getBoolean("new") == true
            BudgetSetupScreen(
                budgetId = id,
                createNew = isNew,
                onDone = { newId ->
                    navController.popBackStack()
                    // After creating a budget, show it.
                    if (isNew) navController.navigate(Routes.budget(newId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BUDGET, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            BudgetDetailScreen(
                budgetId = entry.arguments!!.getLong("id"),
                onBack = { navController.popBackStack() },
                onEdit = actions.editBudget,
            )
        }
        composable(Routes.BUDGETS) {
            BudgetsScreen(onBack = { navController.popBackStack() }, onOpen = actions.openBudget, onNew = actions.newBudget)
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RECURRING) { RecurringScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PAYMENTS) { PaymentSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ACTIVITY) { ActivityScreen(onBack = { navController.popBackStack() }) }
    }
}

/**
 * Bottom-tab graph: tapping a tab cross-fades; going back to Home (button or gesture) slides
 * the current tab away like any other page.
 */
@Composable
fun TabNavHost(
    navController: NavHostController,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val fade = tween<Float>(200)
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(fade) },
        exitTransition = { fadeOut(fade) },
        popEnterTransition = popEnter,
        popExitTransition = popExit,
        predictivePopEnterTransition = predictivePopEnter,
        predictivePopExitTransition = predictivePopExit,
    ) {
        composable(TopLevelDestination.HOME.route) { Page { HomeScreen(actions) } }
        composable(TopLevelDestination.HISTORY.route) { Page { HistoryScreen(onOpenActivity = actions.openActivity) } }
        composable(TopLevelDestination.INSIGHTS.route) { Page { InsightsScreen() } }
        composable(TopLevelDestination.SETTINGS.route) {
            Page { SettingsScreen(actions) }
        }
    }
}
