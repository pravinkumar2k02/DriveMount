package com.drivemount.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.drivemount.app.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing imported Google OAuth credentials.
 */
data class OAuthCredentials(
    val fileName: String,
    val clientId: String,
    val clientSecret: String,
    val projectId: String,
)

/**
 * Manages Google OAuth client credentials dynamically provided by the user
 * from a selected JSON file (e.g. client_secret_xxx.json).
 *
 * No secrets are baked into the application binary.
 */
@Singleton
class OAuthCredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {
    @Volatile
    private var cachedClientId: String = ""
    @Volatile
    private var cachedClientSecret: String = ""
    @Volatile
    private var cachedFileName: String = ""

    /** Observable name of the currently loaded credentials file. */
    val credentialsFileName: Flow<String> = settingsStore.credentialsFileName

    /** Observable Web Client ID. */
    val webClientId: Flow<String> = settingsStore.webClientId

    init {
        CoroutineScope(Dispatchers.IO).launch {
            settingsStore.webClientId.collect { cachedClientId = it }
        }
        CoroutineScope(Dispatchers.IO).launch {
            settingsStore.webClientSecret.collect { cachedClientSecret = it }
        }
        CoroutineScope(Dispatchers.IO).launch {
            settingsStore.credentialsFileName.collect { cachedFileName = it }
        }
    }

    /**
     * Reads, parses, and securely stores credentials from a user-selected JSON file URI.
     */
    suspend fun importCredentialsFromUri(uri: Uri): Result<OAuthCredentials> = withContext(Dispatchers.IO) {
        try {
            // Persist read access across reboots
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w(e, "Could not take persistable URI permission")
            }

            // Extract file display name
            var fileName = "client_secret.json"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }

            // Read and parse JSON content
            val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: throw IOException("Could not read credentials file from selected URI")

            val root = json.parseToJsonElement(jsonText).jsonObject
            val web = root["web"]?.jsonObject
                ?: root["installed"]?.jsonObject
                ?: throw IllegalArgumentException("Invalid client credentials JSON. Must contain a 'web' or 'installed' client configuration.")

            val clientId = web["client_id"]?.jsonPrimitive?.content?.trim() ?: ""
            val clientSecret = web["client_secret"]?.jsonPrimitive?.content?.trim() ?: ""
            val projectId = root["project_id"]?.jsonPrimitive?.content?.trim()
                ?: web["project_id"]?.jsonPrimitive?.content?.trim()
                ?: ""

            if (clientId.isBlank()) {
                throw IllegalArgumentException("Client ID ('client_id') not found in credentials JSON.")
            }

            cachedClientId = clientId
            cachedClientSecret = clientSecret
            cachedFileName = fileName

            // Save to encrypted app settings
            settingsStore.saveOAuthCredentials(
                fileUri = uri.toString(),
                fileName = fileName,
                clientId = clientId,
                clientSecret = clientSecret,
                projectId = projectId,
            )

            Timber.i("Successfully imported OAuth credentials from %s (Client ID: %s)", fileName, clientId)
            Result.success(OAuthCredentials(fileName, clientId, clientSecret, projectId))
        } catch (e: Exception) {
            Timber.e(e, "Failed to import OAuth credentials from URI")
            Result.failure(e)
        }
    }

    /**
     * Synchronously returns the cached Web Client ID.
     */
    fun getClientId(): String {
        if (cachedClientId.isNotBlank()) return cachedClientId
        return runBlocking(Dispatchers.IO) { settingsStore.webClientId.first() }
    }

    /**
     * Synchronously returns the cached Web Client Secret.
     */
    fun getClientSecret(): String {
        if (cachedClientSecret.isNotBlank()) return cachedClientSecret
        return runBlocking(Dispatchers.IO) { settingsStore.webClientSecret.first() }
    }

    /**
     * Clears all stored credentials and unbinds the credentials file.
     */
    suspend fun clearCredentials() {
        cachedClientId = ""
        cachedClientSecret = ""
        cachedFileName = ""
        settingsStore.clearOAuthCredentials()
    }
}
