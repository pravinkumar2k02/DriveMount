package com.drivemount.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.auth.OAuthCredentialsManager
import com.drivemount.app.data.cache.BlockCache
import com.drivemount.app.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for application preferences, block cache management, and OAuth credentials file management.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val blockCache: BlockCache,
    private val credentialsManager: OAuthCredentialsManager,
) : ViewModel() {

    val cacheSizeMb: StateFlow<Int> = settingsStore.cacheSizeMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 512)

    val wifiOnlyUploads: StateFlow<Boolean> = settingsStore.wifiOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)

    val chargingOnlyUploads: StateFlow<Boolean> = settingsStore.chargingOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    val credentialsFileName: StateFlow<String> = credentialsManager.credentialsFileName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), "")

    val webClientId: StateFlow<String> = credentialsManager.webClientId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), "")

    private val _cacheUsedBytes = MutableStateFlow(blockCache.getCurrentSizeBytes())
    val cacheUsedBytes: StateFlow<Long> = _cacheUsedBytes.asStateFlow()

    fun setCacheSize(mb: Int) {
        viewModelScope.launch {
            settingsStore.setCacheSizeMb(mb)
            blockCache.setMaxCapacityMb(mb)
            _cacheUsedBytes.value = blockCache.getCurrentSizeBytes()
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setWifiOnlyUploads(enabled)
        }
    }

    fun setChargingOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setChargingOnlyUploads(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            blockCache.evictAll()
            _cacheUsedBytes.value = blockCache.getCurrentSizeBytes()
        }
    }

    fun importCredentials(uri: Uri) {
        viewModelScope.launch {
            credentialsManager.importCredentialsFromUri(uri)
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            credentialsManager.clearCredentials()
        }
    }
}
