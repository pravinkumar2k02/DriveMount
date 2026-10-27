package com.drivemount.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drivemount.app.ui.accounts.AccountsScreen
import com.drivemount.app.ui.settings.SettingsScreen
import com.drivemount.app.ui.signin.SignInScreen
import com.drivemount.app.ui.uploads.UploadsScreen

/**
 * Screen destinations in the DriveMount application.
 */
sealed class Screen(val route: String) {
    data object SignIn : Screen("sign_in")
    data object Accounts : Screen("accounts")
    data object Settings : Screen("settings")
    data object Uploads : Screen("uploads")
}

/**
 * Main navigation graph for DriveMount.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Accounts.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate(Screen.Accounts.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Accounts.route) {
            AccountsScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToUploads = { navController.navigate(Screen.Uploads.route) },
                onNavigateToSignIn = { navController.navigate(Screen.SignIn.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Uploads.route) {
            UploadsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

