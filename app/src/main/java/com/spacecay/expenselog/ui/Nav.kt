package com.spacecay.expenselog.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spacecay.expenselog.ui.screens.AutoLogScreen
import com.spacecay.expenselog.ui.screens.BackupScreen
import com.spacecay.expenselog.ui.screens.BooksScreen
import com.spacecay.expenselog.ui.screens.BudgetDetailScreen
import com.spacecay.expenselog.ui.screens.BudgetEditScreen
import com.spacecay.expenselog.ui.screens.BudgetsScreen
import com.spacecay.expenselog.ui.screens.CategoriesScreen
import com.spacecay.expenselog.ui.screens.CategoryEditScreen
import com.spacecay.expenselog.ui.screens.CategoryTxnsScreen
import com.spacecay.expenselog.ui.screens.CurrenciesScreen
import com.spacecay.expenselog.ui.screens.DailyLogScreen
import com.spacecay.expenselog.ui.screens.DebtDetailScreen
import com.spacecay.expenselog.ui.screens.DebtEditScreen
import com.spacecay.expenselog.ui.screens.DebtsScreen
import com.spacecay.expenselog.ui.screens.GoalDetailScreen
import com.spacecay.expenselog.ui.screens.GoalEditScreen
import com.spacecay.expenselog.ui.screens.GoalsScreen
import com.spacecay.expenselog.ui.screens.HomeScreen
import com.spacecay.expenselog.ui.screens.RecurringEditScreen
import com.spacecay.expenselog.ui.screens.RecurringScreen
import com.spacecay.expenselog.ui.screens.ReviewScreen
import com.spacecay.expenselog.ui.screens.SearchScreen
import com.spacecay.expenselog.ui.screens.SettingsScreen
import com.spacecay.expenselog.ui.screens.TemplateEditScreen
import com.spacecay.expenselog.ui.screens.TemplatesScreen
import com.spacecay.expenselog.ui.screens.TxDetailScreen
import com.spacecay.expenselog.ui.screens.TxEditScreen
import com.spacecay.expenselog.ui.screens.WalletDetailScreen
import com.spacecay.expenselog.ui.screens.WalletEditScreen
import com.spacecay.expenselog.ui.screens.WalletsManageScreen

private fun longArg(name: String): NamedNavArgument = navArgument(name) { type = NavType.LongType; defaultValue = 0L }
private fun intArg(name: String): NamedNavArgument = navArgument(name) { type = NavType.IntType; defaultValue = 0 }

private fun NavBackStackEntry.long(name: String) = arguments?.getLong(name) ?: 0L
private fun NavBackStackEntry.int(name: String) = arguments?.getInt(name) ?: 0

private fun NavGraphBuilder.screen(route: String, args: List<NamedNavArgument> = emptyList(), content: @Composable (NavBackStackEntry) -> Unit) {
    composable(
        route, args,
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(220)) },
    ) { content(it) }
}

@Composable
fun AppNav(nav: NavHostController) {
    val go: (String) -> Unit = { r -> nav.navigate(r) { launchSingleTop = true } }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }

    LaunchedEffect(Unit) {
        UiBus.pendingRoute.collect { r -> if (r != null) { UiBus.pendingRoute.value = null; go(r) } }
    }

    NavHost(nav, startDestination = "home") {
        screen("home") { HomeScreen(go) }
        screen("txedit?id={id}&type={type}&template={template}&wallet={wallet}", listOf(longArg("id"), intArg("type"), longArg("template"), longArg("wallet"))) {
            TxEditScreen(it.long("id"), it.int("type"), it.long("template"), it.long("wallet"), onClose = back, onManageCategories = { go("categories") })
        }
        screen("tx/{id}", listOf(longArg("id"))) { TxDetailScreen(it.long("id"), back, go) }
        screen("review") { ReviewScreen(back, go) }
        screen("search") { SearchScreen(back, go) }
        screen("cattx/{id}?from={from}&to={to}", listOf(longArg("id"), longArg("from"), longArg("to"))) {
            CategoryTxnsScreen(it.long("id"), it.long("from"), it.long("to"), back, go)
        }
        screen("wallet/{id}", listOf(longArg("id"))) { WalletDetailScreen(it.long("id"), back, go) }
        screen("walletedit?id={id}", listOf(longArg("id"))) { WalletEditScreen(it.long("id"), back) }
        screen("wallets/manage") { WalletsManageScreen(back, go) }
        screen("categories") { CategoriesScreen(back, go) }
        screen("catedit?id={id}&type={type}&parent={parent}", listOf(longArg("id"), intArg("type"), longArg("parent"))) {
            CategoryEditScreen(it.long("id"), it.int("type"), it.long("parent"), back)
        }
        screen("currencies") { CurrenciesScreen(back) }
        screen("budgets") { BudgetsScreen(back, go) }
        screen("budget/{id}", listOf(longArg("id"))) { BudgetDetailScreen(it.long("id"), back, go) }
        screen("budgetedit?id={id}", listOf(longArg("id"))) { BudgetEditScreen(it.long("id"), back) }
        screen("goals") { GoalsScreen(back, go) }
        screen("goal/{id}", listOf(longArg("id"))) { GoalDetailScreen(it.long("id"), back, go) }
        screen("goaledit?id={id}", listOf(longArg("id"))) { GoalEditScreen(it.long("id"), back) }
        screen("debts") { DebtsScreen(back, go) }
        screen("debt/{id}", listOf(longArg("id"))) { DebtDetailScreen(it.long("id"), back, go) }
        screen("debtedit?id={id}&type={type}", listOf(longArg("id"), intArg("type"))) { DebtEditScreen(it.long("id"), it.int("type"), back) }
        screen("recurring") { RecurringScreen(back, go) }
        screen("recurringedit?id={id}", listOf(longArg("id"))) { RecurringEditScreen(it.long("id"), back) }
        screen("templates") { TemplatesScreen(back, go) }
        screen("templateedit?id={id}", listOf(longArg("id"))) { TemplateEditScreen(it.long("id"), back) }
        screen("autolog") { AutoLogScreen(back, go) }
        screen("dailylog") { DailyLogScreen(back, go) }
        screen("backup") { BackupScreen(back) }
        screen("settings") { SettingsScreen(back, go) }
        screen("books") { BooksScreen(back) }
        screen("account") { com.spacecay.expenselog.ui.screens.AccountScreen(back) }
    }
}
