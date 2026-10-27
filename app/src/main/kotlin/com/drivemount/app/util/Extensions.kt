package com.drivemount.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.api.DriveFile
import com.drivemount.app.data.db.DriveFileEntity
import java.text.DecimalFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Extension and utility functions for the DriveMount application.
 */

/**
 * Formats a byte count into a human-readable string (e.g. "1.50 GB", "320.00 MB").
 */
fun Long.toReadableSize(): String {
    if (this <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    val value = this / Math.pow(1024.0, digitGroups.toDouble())
    val format = DecimalFormat("#,##0.##")
    return "${format.format(value)} ${units[digitGroups]}"
}

/**
 * Checks whether the given MIME type corresponds to a proprietary Google Workspace document.
 */
fun String.isGoogleDocsType(): Boolean {
    return this.startsWith("application/vnd.google-apps.") &&
        this != DriveApiConstants.MIME_GOOGLE_FOLDER &&
        this != DriveApiConstants.MIME_GOOGLE_SHORTCUT
}

/**
 * Resolves the preferred export MIME type for Google Workspace documents.
 */
fun String.toExportMimeType(): String? {
    return DriveApiConstants.EXPORT_MIME_TYPE_MAP[this]
}

/**
 * Maps a [DriveFile] API model into a [DriveFileEntity] for Room persistence.
 */
fun DriveFile.toEntity(accountId: String): DriveFileEntity {
    val lastModEpoch = modifiedTime?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (e: DateTimeParseException) {
            System.currentTimeMillis()
        }
    } ?: System.currentTimeMillis()

    val isGoogleType = mimeType.isGoogleDocsType()
    val exportMime = if (isGoogleType) {
        mimeType.toExportMimeType() ?: "application/pdf"
    } else {
        null
    }

    return DriveFileEntity(
        fileId = id,
        accountId = accountId,
        name = name,
        mimeType = mimeType,
        size = size ?: 0L,
        lastModified = lastModEpoch,
        parentId = parents?.firstOrNull(),
        trashed = trashed,
        thumbnailLink = thumbnailLink,
        webContentLink = webContentLink,
        webViewLink = webViewLink,
        canEdit = capabilities?.canEdit ?: false,
        canDelete = capabilities?.canTrash ?: capabilities?.canDelete ?: false,
        canRename = capabilities?.canRename ?: capabilities?.canEdit ?: false,
        canMoveFrom = capabilities?.canMoveItemOutOfDrive ?: capabilities?.canEdit ?: false,
        canMoveTo = capabilities?.canAddChildren ?: false,
        canAddChildren = capabilities?.canAddChildren ?: false,
        exportMimeType = exportMime,
        cachedAt = System.currentTimeMillis()
    )
}

/**
 * Checks if active network connectivity is currently available.
 */
fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Checks if the current active network is unmetered (e.g. Wi-Fi).
 */
fun Context.isNetworkUnmetered(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

/**
 * Generates a unique UUID string for upload tracking.
 */
fun generateUploadId(): String = UUID.randomUUID().toString()

