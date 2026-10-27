package com.drivemount.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single file or folder resource from the Google Drive API v3.
 *
 * @property id The unique opaque ID of the file.
 * @property name The name of the file (title).
 * @property mimeType The MIME type of the file.
 * @property size The size of the file in bytes (null for Google Workspace types).
 * @property modifiedTime RFC 3339 timestamp of the last modification.
 * @property parents List of parent folder IDs.
 * @property trashed Whether the file has been trashed.
 * @property thumbnailLink A short-lived link to a thumbnail image.
 * @property webContentLink A link for downloading the file content.
 * @property webViewLink A link for opening the file in a web browser.
 * @property capabilities The capabilities the current user has on this file.
 * @property exportLinks A mapping of MIME type to export download URL for Google Workspace files.
 */
@Serializable
data class DriveFile(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("mimeType")
    val mimeType: String,

    @SerialName("size")
    val size: Long? = null,

    @SerialName("modifiedTime")
    val modifiedTime: String? = null,

    @SerialName("parents")
    val parents: List<String>? = null,

    @SerialName("trashed")
    val trashed: Boolean = false,

    @SerialName("thumbnailLink")
    val thumbnailLink: String? = null,

    @SerialName("webContentLink")
    val webContentLink: String? = null,

    @SerialName("webViewLink")
    val webViewLink: String? = null,

    @SerialName("capabilities")
    val capabilities: DriveCapabilities? = null,

    @SerialName("exportLinks")
    val exportLinks: Map<String, String>? = null,
) {
    companion object {
        // ── Google Workspace MIME types ───────────────────────────────

        /** MIME type for Google Docs documents. */
        const val MIME_DOCUMENT = "application/vnd.google-apps.document"

        /** MIME type for Google Sheets spreadsheets. */
        const val MIME_SPREADSHEET = "application/vnd.google-apps.spreadsheet"

        /** MIME type for Google Slides presentations. */
        const val MIME_PRESENTATION = "application/vnd.google-apps.presentation"

        /** MIME type for Google Drawings. */
        const val MIME_DRAWING = "application/vnd.google-apps.drawing"

        /** MIME type for Google Forms. */
        const val MIME_FORM = "application/vnd.google-apps.form"

        /** MIME type for Google Apps Script projects. */
        const val MIME_SCRIPT = "application/vnd.google-apps.script"

        /** MIME type for Google Sites. */
        const val MIME_SITE = "application/vnd.google-apps.site"

        /** MIME type for Google Jamboard. */
        const val MIME_JAM = "application/vnd.google-apps.jam"

        /** MIME type for Google Maps. */
        const val MIME_MAP = "application/vnd.google-apps.map"

        /** MIME type for Google Drive folders. */
        const val MIME_FOLDER = "application/vnd.google-apps.folder"

        /** MIME type for Google Drive shortcuts. */
        const val MIME_SHORTCUT = "application/vnd.google-apps.shortcut"

        // ── Export MIME types ─────────────────────────────────────────

        /** Microsoft Word format. */
        const val EXPORT_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        /** Microsoft Excel format. */
        const val EXPORT_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** Microsoft PowerPoint format. */
        const val EXPORT_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"

        /** PDF format. */
        const val EXPORT_PDF = "application/pdf"

        /** PNG image format. */
        const val EXPORT_PNG = "image/png"

        /** SVG image format. */
        const val EXPORT_SVG = "image/svg+xml"

        /** JSON format for Apps Script. */
        const val EXPORT_SCRIPT_JSON = "application/vnd.google-apps.script+json"

        /**
         * Maps each Google Workspace MIME type to its preferred export MIME type.
         */
        val EXPORT_MAP: Map<String, String> = mapOf(
            MIME_DOCUMENT to EXPORT_DOCX,
            MIME_SPREADSHEET to EXPORT_XLSX,
            MIME_PRESENTATION to EXPORT_PPTX,
            MIME_DRAWING to EXPORT_PNG,
            MIME_FORM to EXPORT_PDF,
            MIME_SCRIPT to EXPORT_SCRIPT_JSON,
            MIME_SITE to EXPORT_PDF,
            MIME_JAM to EXPORT_PDF,
            MIME_MAP to EXPORT_PDF,
        )

        /**
         * Set of all Google Workspace MIME types (not downloadable via alt=media).
         */
        val GOOGLE_WORKSPACE_TYPES: Set<String> = setOf(
            MIME_DOCUMENT,
            MIME_SPREADSHEET,
            MIME_PRESENTATION,
            MIME_DRAWING,
            MIME_FORM,
            MIME_SCRIPT,
            MIME_SITE,
            MIME_JAM,
            MIME_MAP,
            MIME_SHORTCUT,
        )
    }
}

/**
 * Capabilities that the current user has on a [DriveFile].
 */
@Serializable
data class DriveCapabilities(
    @SerialName("canEdit")
    val canEdit: Boolean = false,

    @SerialName("canDelete")
    val canDelete: Boolean = false,

    @SerialName("canRename")
    val canRename: Boolean = false,

    @SerialName("canMoveItemOutOfDrive")
    val canMoveItemOutOfDrive: Boolean = false,

    @SerialName("canMoveItemWithinDrive")
    val canMoveItemWithinDrive: Boolean = false,

    @SerialName("canAddChildren")
    val canAddChildren: Boolean = false,

    @SerialName("canCopy")
    val canCopy: Boolean = false,

    @SerialName("canDownload")
    val canDownload: Boolean = false,

    @SerialName("canShare")
    val canShare: Boolean = false,

    @SerialName("canTrash")
    val canTrash: Boolean = false,

    @SerialName("canUntrash")
    val canUntrash: Boolean = false,
)

/**
 * Response model for the Drive API `files.list` endpoint.
 *
 * @property files The list of [DriveFile] resources returned.
 * @property nextPageToken Token for retrieving the next page of results, or null.
 * @property incompleteSearch Whether the search process was incomplete.
 */
@Serializable
data class DriveFileList(
    @SerialName("files")
    val files: List<DriveFile> = emptyList(),

    @SerialName("nextPageToken")
    val nextPageToken: String? = null,

    @SerialName("incompleteSearch")
    val incompleteSearch: Boolean = false,
)

/**
 * Storage quota information from the Drive API `about` endpoint.
 *
 * @property storageQuota Detailed storage quota breakdown.
 */
@Serializable
data class DriveAbout(
    @SerialName("storageQuota")
    val storageQuota: StorageQuota,
)

/**
 * Detailed storage quota breakdown for a Google Drive account.
 *
 * @property limit Total storage limit in bytes (null for unlimited).
 * @property usage Total storage used in bytes.
 * @property usageInDrive Storage used by Drive files in bytes.
 * @property usageInTrash Storage used by trashed files in bytes.
 */
@Serializable
data class StorageQuota(
    @SerialName("limit")
    val limit: Long? = null,

    @SerialName("usage")
    val usage: Long? = null,

    @SerialName("usageInDrive")
    val usageInDrive: Long? = null,

    @SerialName("usageInTrash")
    val usageInTrash: Long? = null,
)

/**
 * A single change record from the Drive API `changes.list` endpoint.
 *
 * @property fileId The ID of the file that changed.
 * @property file The updated [DriveFile] resource (null if removed).
 * @property removed Whether the file was removed from the user's view.
 * @property changeType The type of change ("file" or "drive").
 * @property time RFC 3339 timestamp when the change occurred.
 */
@Serializable
data class DriveChange(
    @SerialName("fileId")
    val fileId: String? = null,

    @SerialName("file")
    val file: DriveFile? = null,

    @SerialName("removed")
    val removed: Boolean = false,

    @SerialName("changeType")
    val changeType: String? = null,

    @SerialName("time")
    val time: String? = null,
)

/**
 * Response model for the Drive API `changes.list` endpoint.
 *
 * @property changes List of [DriveChange] records in this page.
 * @property nextPageToken Token for the next page of changes, or null.
 * @property newStartPageToken The new start page token for future polling (present on the last page).
 */
@Serializable
data class DriveChangeList(
    @SerialName("changes")
    val changes: List<DriveChange> = emptyList(),

    @SerialName("nextPageToken")
    val nextPageToken: String? = null,

    @SerialName("newStartPageToken")
    val newStartPageToken: String? = null,
)

/**
 * Response from the Drive API `changes.getStartPageToken` endpoint.
 *
 * @property startPageToken The starting page token for watching changes.
 */
@Serializable
data class DriveStartPageToken(
    @SerialName("startPageToken")
    val startPageToken: String,
)

/**
 * Response from the Drive API when querying export formats.
 *
 * @property exportFormats Mapping of source MIME type to a list of target export MIME types.
 */
@Serializable
data class ExportFormats(
    @SerialName("exportFormats")
    val exportFormats: Map<String, List<String>> = emptyMap(),
)
