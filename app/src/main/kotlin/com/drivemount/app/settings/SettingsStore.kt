package com.drivemount.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivemount.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drivemount_settings")

/**
 * Manages user preferences and configuration using Jetpack DataStore.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    companion object {
        private val KEY_CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_uploads")
        private val KEY_CHARGING_ONLY = booleanPreferencesKey("charging_only_uploads")
    }

    /** Maximum disk cache size in megabytes. Default: 512 MB. */
    val cacheSizeMb: Flow<Int> = dataStore.data.map { preferences ->
        preferences[KEY_CACHE_SIZE_MB] ?: Constants.DEFAULT_CACHE_SIZE_MB
    }

    /** Whether file uploads should only occur over unmetered (Wi-Fi) connections. Default: true. */
    val wifiOnlyUploads: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_WIFI_ONLY] ?: true
    }

    /** Whether file uploads should only occur while the device is charging. Default: false. */
    val chargingOnlyUploads: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEY_CHARGING_ONLY] ?: false
    }

    /** Updates the maximum cache size setting in megabytes. */
    suspend fun setCacheSizeMb(value: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_CACHE_SIZE_MB] = value
        }
    }

    /** Updates the Wi-Fi-only uploads setting. */
    suspend fun setWifiOnlyUploads(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_WIFI_ONLY] = value
        }
    }

    /** Updates the charging-only uploads setting. */
    suspend fun setChargingOnlyUploads(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_CHARGING_ONLY] = value
        }
    }
}

