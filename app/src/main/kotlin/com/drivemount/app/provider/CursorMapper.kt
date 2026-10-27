package com.drivemount.app.provider

import android.database.MatrixCursor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.db.DriveFileEntity

/**
 * Utility object that maps [DriveFileEntity] instances and account metadata
 * into [MatrixCursor] rows conforming to the Android Storage Access Framework
 * (SAF) contracts.
 *
 * This mapper handles the translation of Google Drive–specific MIME types
 * (Google Docs, Sheets, Slides, etc.) into their export-equivalent MIME types,
 * and computes the appropriate SAF document flags based on the file's
 * capabilities.
 *
 * @see android.provider.DocumentsContract.Root
 * @see android.provider.DocumentsContract.Document
 */
object CursorMapper {

    /**
     * Default projection for root queries.
     *
     * These columns are returned when the caller does not specify a projection
     * in [android.provider.DocumentsProvider.queryRoots].
     */
    val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_AVAILABLE_BYTES,
        Root.COLUMN_CAPACITY_BYTES,
        Root.COLUMN_MIME_TYPES,
    )

    /**
     * Default projection for document queries.
     *
     * These columns are returned when the caller does not specify a projection
     * in [android.provider.DocumentsProvider.queryDocument] or
     * [android.provider.DocumentsProvider.queryChildDocuments].
     */
    val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_ICON,
        Document.COLUMN_SUMMARY,
    )

    /**
     * Adds a row to the given [cursor] representing a single Drive file or folder.
     *
     * Google Workspace types (Google Docs, Sheets, Slides, etc.) are automatically
     * mapped to their export MIME types (DOCX, XLSX, PPTX, etc.) so that SAF
     * clients see a downloadable file type.
     *
     * Document flags are computed from the entity's capability booleans:
     * - [Document.FLAG_SUPPORTS_WRITE] — if the user can edit and it's not a Google Docs type
     * - [Document.FLAG_SUPPORTS_DELETE] — if the user can delete
     * - [Document.FLAG_SUPPORTS_RENAME] — if the user can rename
     * - [Document.FLAG_SUPPORTS_MOVE] — if the user can move from the current parent
     * - [Document.FLAG_SUPPORTS_COPY] — for non-folder files
     * - [Document.FLAG_SUPPORTS_THUMBNAIL] — if a thumbnail link is available
     * - [Document.FLAG_DIR_SUPPORTS_CREATE] — for folders where the user can add children
     *
     * @param cursor The [MatrixCursor] to add the row to.
     * @param entity The [DriveFileEntity] containing file metadata.
     * @param accountId The account identifier used to build the compound document ID.
     */
    fun addFileRow(cursor: MatrixCursor, entity: DriveFileEntity, accountId: String) {
        val documentId = buildDocumentId(accountId, entity.fileId)

        val isGoogleType = entity.mimeType.startsWith("application/vnd.google-apps.")
        val isFolder = entity.mimeType == Document.MIME_TYPE_DIR ||
            entity.mimeType == DriveApiConstants.MIME_GOOGLE_FOLDER

        // For Google Workspace types, present the export MIME type to SAF clients
        val displayMimeType = when {
            isFolder -> Document.MIME_TYPE_DIR
            isGoogleType -> entity.exportMimeType
                ?: DriveApiConstants.EXPORT_MIME_TYPE_MAP[entity.mimeType]
                ?: "application/pdf"
            else -> entity.mimeType
        }

        var flags = 0
        if (entity.canEdit && !isGoogleType) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (entity.canDelete) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE
        }
        if (entity.canRename) {
            flags = flags or Document.FLAG_SUPPORTS_RENAME
        }
        if (entity.canMoveFrom) {
            flags = flags or Document.FLAG_SUPPORTS_MOVE
        }
        if (entity.thumbnailLink != null) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }
        if (isFolder && entity.canAddChildren) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (!isFolder) {
            flags = flags or Document.FLAG_SUPPORTS_COPY
        }

        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(Document.COLUMN_DISPLAY_NAME, entity.name)
        row.add(Document.COLUMN_MIME_TYPE, displayMimeType)
        row.add(Document.COLUMN_SIZE, entity.size)
        row.add(Document.COLUMN_LAST_MODIFIED, entity.lastModified)
        row.add(Document.COLUMN_FLAGS, flags)
        row.add(Document.COLUMN_ICON, null)
        row.add(Document.COLUMN_SUMMARY, null)
    }

    /**
     * Resolves the projection array for a cursor.
     *
     * If the caller provides a specific projection, it is returned as-is.
     * Otherwise, the provided [default] projection is used.
     *
     * @param requested The projection requested by the caller, or null.
     * @param default The default projection to use when [requested] is null.
     * @return The resolved projection array.
     */
    fun resolveProjection(
        requested: Array<out String>?,
        default: Array<String>,
    ): Array<out String> {
        return requested ?: default
    }

    /**
     * Builds a compound document ID from an account identifier and a Drive file ID.
     *
     * The format is `"accountId:fileId"`, which allows the provider to route
     * requests to the correct account.
     *
     * @param accountId The account email or unique identifier.
     * @param fileId The Google Drive file ID.
     * @return The compound document ID string.
     */
    fun buildDocumentId(accountId: String, fileId: String): String {
        return "$accountId:$fileId"
    }

    /**
     * Parses a compound document ID into its account and file ID components.
     *
     * @param documentId The compound document ID in the format `"accountId:fileId"`.
     * @return A [Pair] of (accountId, fileId).
     * @throws IllegalArgumentException if the document ID does not contain exactly one colon separator.
     */
    fun parseDocumentId(documentId: String): Pair<String, String> {
        val colonIndex = documentId.indexOf(':')
        require(colonIndex > 0 && colonIndex < documentId.length - 1) {
            "Invalid document ID format: '$documentId'. Expected 'accountId:fileId'."
        }
        val accountId = documentId.substring(0, colonIndex)
        val fileId = documentId.substring(colonIndex + 1)
        return accountId to fileId
    }
}
