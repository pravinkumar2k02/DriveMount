package com.drivemount.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        private val KEY_CREDENTIALS_FILE_URI = stringPreferencesKey("credentials_file_uri")
        private val KEY_CREDENTIALS_FILE_NAME = stringPreferencesKey("credentials_file_name")
        private val KEY_WEB_CLIENT_ID = stringPreferencesKey("web_client_id")
        private val KEY_WEB_CLIENT_SECRET = stringPreferencesKey("web_client_secret")
        private val KEY_PROJECT_ID = stringPreferencesKey("project_id")
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

    /** Display name of the imported credentials JSON file. */
    val credentialsFileName: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_CREDENTIALS_FILE_NAME] ?: ""
    }

    /** URI of the imported credentials JSON file. */
    val credentialsFileUri: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_CREDENTIALS_FILE_URI] ?: ""
    }

    /** Web Client ID imported from credentials JSON. */
    val webClientId: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_WEB_CLIENT_ID] ?: ""
    }

    /** Web Client Secret imported from credentials JSON. */
    val webClientSecret: Flow<String> = dataStore.data.map { preferences ->
        preferences[KEY_WEB_CLIENT_SECRET] ?: ""
    }

    /** Saves imported OAuth client credentials. */
    suspend fun saveOAuthCredentials(
        fileUri: String,
        fileName: String,
        clientId: String,
        clientSecret: String,
        projectId: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_CREDENTIALS_FILE_URI] = fileUri
            preferences[KEY_CREDENTIALS_FILE_NAME] = fileName
            preferences[KEY_WEB_CLIENT_ID] = clientId.trim()
            preferences[KEY_WEB_CLIENT_SECRET] = clientSecret.trim()
            preferences[KEY_PROJECT_ID] = projectId.trim()
        }
    }

    /** Clears stored OAuth credentials and file association. */
    suspend fun clearOAuthCredentials() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_CREDENTIALS_FILE_URI)
            preferences.remove(KEY_CREDENTIALS_FILE_NAME)
            preferences.remove(KEY_WEB_CLIENT_ID)
            preferences.remove(KEY_WEB_CLIENT_SECRET)
            preferences.remove(KEY_PROJECT_ID)
        }
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

