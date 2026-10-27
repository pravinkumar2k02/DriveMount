package com.drivemount.app.ui.signin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.auth.AuthManager
import com.drivemount.app.data.db.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the sign-in screen.
 */
sealed interface SignInUiState {
    /** No action in progress. */
    data object Idle : SignInUiState

    /** A sign-in attempt is currently in progress. */
    data object Loading : SignInUiState

    /** Sign-in succeeded; contains the newly added [account]. */
    data class Success(val account: AccountEntity) : SignInUiState

    /** Sign-in failed with a human-readable [message]. */
    data class Error(val message: String) : SignInUiState
}

/**
 * ViewModel for the sign-in screen.
 *
 * Orchestrates the Google account sign-in flow through [AuthManager] and
 * exposes a [SignInUiState] so the UI can react to progress and results.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)

    /** Observable sign-in state. */
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /**
     * Initiates the Google sign-in flow.
     *
     * The [activityContext] is required because the underlying OAuth consent
     * screen must be launched from an Activity context.
     *
     * @param activityContext The Activity [Context] hosting the sign-in screen.
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
     *
     * Call this after consuming a [SignInUiState.Error] event (e.g. after
     * the snackbar has been shown).
     */
    fun resetState() {
        _uiState.value = SignInUiState.Idle
    }
}
