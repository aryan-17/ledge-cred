package com.ledgecred.ccsettleapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ledgecred.ccsettleapp.ui.history.HistoryScreen
import com.ledgecred.ccsettleapp.ui.home.HomeScreen
import com.ledgecred.ccsettleapp.ui.onboarding.OnboardingScreen
import com.ledgecred.ccsettleapp.ui.review.ReviewScreen
import com.ledgecred.ccsettleapp.ui.settle.PartialReceiptScreen
import com.ledgecred.ccsettleapp.ui.settle.SettleScreen
import com.ledgecred.ccsettleapp.ui.settings.SettingsScreen
import com.ledgecred.ccsettleapp.ui.transactions.TransactionsScreen
import com.ledgecred.ccsettleapp.ui.waiting.WaitingScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onSettleTap   = { eventId -> navController.navigate(Screen.Settle.route(eventId)) },
                onReviewTap   = { navController.navigate(Screen.Review.route) },
                onHistoryTap  = { navController.navigate(Screen.History.route) },
                onSettingsTap = { navController.navigate(Screen.Settings.route) },
                onSeeAllTap   = { navController.navigate(Screen.Transactions.route) }
            )
        }

        composable(
            route = Screen.Settle.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId") ?: return@composable
            SettleScreen(
                eventId   = eventId,
                onPaid    = { navController.navigate(Screen.Waiting.route(eventId)) {
                    popUpTo(Screen.Home.route)
                }},
                onBack    = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Waiting.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId") ?: return@composable
            WaitingScreen(
                eventId         = eventId,
                onCleared       = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } },
                onPartial       = { navController.navigate(Screen.PartialReceipt.route(eventId)) { popUpTo(Screen.Home.route) } },
                onManualConfirm = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } }
            )
        }

        composable(
            route = Screen.PartialReceipt.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId") ?: return@composable
            PartialReceiptScreen(
                eventId       = eventId,
                onSendRemaining = { remaining -> navController.navigate(Screen.Settle.route(remaining)) },
                onDone          = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } }
            )
        }

        composable(Screen.Transactions.route) { TransactionsScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Review.route)   { ReviewScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.History.route)  { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
