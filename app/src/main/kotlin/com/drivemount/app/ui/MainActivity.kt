package com.drivemount.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.drivemount.app.auth.AuthManager
import com.drivemount.app.ui.navigation.AppNavigation
import com.drivemount.app.ui.navigation.Screen
import com.drivemount.app.ui.theme.DriveMountTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main launcher activity for DriveMount.
 *
 * Hosts Jetpack Compose navigation with edge-to-edge system bars.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DriveMountTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val accounts by authManager.getSignedInAccounts().collectAsState(initial = null)

                    // Determine start destination based on whether user has any signed-in accounts
                    val startDestination = if (accounts == null || accounts?.isEmpty() == true) {
                        Screen.SignIn.route
                    } else {
                        Screen.Accounts.route
                    }

                    if (accounts != null) {
                        AppNavigation(startDestination = startDestination)
                    }
                }
            }
        }
    }
}

