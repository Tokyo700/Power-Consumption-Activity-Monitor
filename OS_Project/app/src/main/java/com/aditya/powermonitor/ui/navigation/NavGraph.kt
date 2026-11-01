package com.aditya.powermonitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aditya.powermonitor.R
import com.aditya.powermonitor.ui.dashboard.DashboardScreen
import com.aditya.powermonitor.ui.debug.DebugScreen
import com.aditya.powermonitor.ui.history.HistoryScreen
import com.aditya.powermonitor.ui.history.HistoryViewModel

/** Route names: each string identifies one screen in the back stack. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val DEBUG = "debug"

    /** "?pkg={pkg}" is an optional argument: open History already filtered to one app. */
    const val HISTORY = "history?${HistoryViewModel.ARG_PACKAGE}={${HistoryViewModel.ARG_PACKAGE}}"

    fun history(packageName: String? = null) =
        if (packageName == null) "history" else "history?${HistoryViewModel.ARG_PACKAGE}=$packageName"
}

/**
 * NavHost swaps which screen is shown. The NavController keeps a back stack, so the system
 * Back gesture (or our arrow) pops back to the previous screen.
 */
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenHistory = { pkg -> navController.navigate(Routes.history(pkg)) },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }
        composable(
            Routes.HISTORY,
            arguments = listOf(
                navArgument(HistoryViewModel.ARG_PACKAGE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            BackScaffold(title = "History", onBack = { navController.popBackStack() }) { modifier ->
                HistoryScreen(modifier)
            }
        }
        composable(Routes.DEBUG) {
            BackScaffold(title = "Debug", onBack = { navController.popBackStack() }) { modifier ->
                DebugScreen(modifier)
            }
        }
    }
}

/** A screen with a top bar and a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding -> content(Modifier.padding(innerPadding)) }
}
