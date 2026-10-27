package com.drivemount.app.ui.signin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.auth.AuthManager
import com.drivemount.app.auth.OAuthCredentialsManager
import com.drivemount.app.data.db.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the sign-in screen.
 */
sealed interface SignInUiState {
    /** No action in progress. */
    data object Idle : SignInUiState

    /** A sign-in or import attempt is currently in progress. */
    data object Loading : SignInUiState

    /** Sign-in succeeded; contains the newly added [account]. */
    data class Success(val account: AccountEntity) : SignInUiState

    /** Sign-in or import failed with a human-readable [message]. */
    data class Error(val message: String) : SignInUiState
}

/**
 * ViewModel for the sign-in screen.
 *
 * Orchestrates dynamic Google OAuth credentials import and user sign-in.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val credentialsManager: OAuthCredentialsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)

    /** Observable sign-in state. */
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Name of the currently imported credentials file (empty if none). */
    val credentialsFileName: StateFlow<String> = credentialsManager.credentialsFileName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Active Web Client ID. */
    val webClientId: StateFlow<String> = credentialsManager.webClientId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /**
     * Imports client credentials dynamically from a user-selected JSON file URI.
     */
    fun importCredentials(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SignInUiState.Loading
            val result = credentialsManager.importCredentialsFromUri(uri)
            if (result.isSuccess) {
                _uiState.value = SignInUiState.Idle
            } else {
                _uiState.value = SignInUiState.Error(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to import client credentials JSON"
                )
            }
        }
    }

    /**
     * Clears imported client credentials.
     */
    fun clearCredentials() {
        viewModelScope.launch {
            credentialsManager.clearCredentials()
        }
    }

    /**
     * Initiates the Google sign-in flow.
     */
    fun signIn(activityContext: Context) {
        if (_uiState.value is SignInUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = SignInUiState.Loading
            try {
                val account = authManager.signIn(activityContext)
                _uiState.value = SignInUiState.Success(account)
            } catch (e: Exception) {
                _uiState.value = SignInUiState.Error(
                    e.localizedMessage ?: "Sign-in failed. Please try again."
                )
            }
        }
    }

    /**
     * Resets the UI state back to [SignInUiState.Idle].
     */
    fun resetState() {
        _uiState.value = SignInUiState.Idle
    }
}
