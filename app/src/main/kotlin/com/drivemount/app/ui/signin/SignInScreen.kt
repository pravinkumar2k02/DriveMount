package com.drivemount.app.ui.signin

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Full-screen sign-in composable.
 *
 * Displays the DriveMount branding and a "Sign in with Google" button.
 * Reacts to [SignInUiState] changes emitted by [SignInViewModel]:
 * - [SignInUiState.Loading] → shows a progress indicator.
 * - [SignInUiState.Error]   → shows a snackbar with the error message.
 * - [SignInUiState.Success] → invokes [onSignInSuccess] to navigate away.
 *
 * @param onSignInSuccess Callback invoked once the user signs in successfully.
 * @param viewModel Hilt-provided [SignInViewModel].
 */
@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Navigate away on success.
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SignInUiState.Success -> onSignInSuccess()
            is SignInUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = "DriveMount logo",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "DriveMount",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Mount your Google Drive as a local drive",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Sign-in button / loading indicator
            if (uiState is SignInUiState.Loading) {
                CircularProgressIndicator()
            } else {
                FilledTonalButton(
                    onClick = {
                        // AuthManager requires an Activity context for the consent UI.
                        val activity = context as? Activity ?: return@FilledTonalButton
                        viewModel.signIn(activity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Sign in with Google")
                }
            }
        }
    }
}
