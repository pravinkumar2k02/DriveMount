package com.drivemount.app.ui.accounts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.auth.AuthManager
import com.drivemount.app.data.db.AccountEntity
import com.drivemount.app.data.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing signed-in Google accounts and viewing storage quotas.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val driveRepository: DriveRepository,
) : ViewModel() {

    /** Observable list of all mounted Google accounts. */
    val accounts: StateFlow<List<AccountEntity>> = authManager.getSignedInAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /** Adds another Google account via Google Sign-In. */
    fun addAccount(activityContext: Context) {
        viewModelScope.launch {
            try {
                authManager.signIn(activityContext)
            } catch (e: Exception) {
                Timber.e(e, "Error adding secondary account")
            }
        }
    }

    /** Removes an account and unmounts its storage root. */
    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            try {
                authManager.removeAccount(accountId)
            } catch (e: Exception) {
                Timber.e(e, "Error removing account %s", accountId)
            }
        }
    }

    /** Refreshes storage quota information from Drive API for an account. */
    fun refreshQuota(accountId: String) {
        viewModelScope.launch {
            try {
                driveRepository.getAbout(accountId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to refresh storage quota for %s", accountId)
            }
        }
    }
}

