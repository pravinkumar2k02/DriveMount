package com.drivemount.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * ViewModel for application preferences, block cache management, and upload network constraints.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val blockCache: BlockCache,
) : ViewModel() {

    val cacheSizeMb: StateFlow<Int> = settingsStore.cacheSizeMb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 512)

    val wifiOnlyUploads: StateFlow<Boolean> = settingsStore.wifiOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)

    val chargingOnlyUploads: StateFlow<Boolean> = settingsStore.chargingOnlyUploads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

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
}

