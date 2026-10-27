package com.drivemount.app.provider

import android.database.MatrixCursor
import android.provider.DocumentsContract.Document
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.db.DriveFileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CursorMapper] verifying conversion from [DriveFileEntity]
 * into Storage Access Framework cursor columns.
 */
class CursorMapperTest {

    @Test
    fun testBuildAndParseDocumentId() {
        val accountId = "user@example.com"
        val fileId = "1a2b3c4d5e"
        val compoundId = CursorMapper.buildDocumentId(accountId, fileId)
        assertEquals("user@example.com:1a2b3c4d5e", compoundId)

        val (parsedAccount, parsedFile) = CursorMapper.parseDocumentId(compoundId)
        assertEquals(accountId, parsedAccount)
        assertEquals(fileId, parsedFile)
    }

    @Test
    fun testResolveProjection_nullReturnsDefault() {
        val resolved = CursorMapper.resolveProjection(null, CursorMapper.DEFAULT_DOCUMENT_PROJECTION)
        assertEquals(CursorMapper.DEFAULT_DOCUMENT_PROJECTION, resolved)
    }

    @Test
    fun testResolveProjection_customPassedThrough() {
        val custom = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        val resolved = CursorMapper.resolveProjection(custom, CursorMapper.DEFAULT_DOCUMENT_PROJECTION)
        assertEquals(custom, resolved)
    }

    @Test
    fun testAddFileRow_regularFile() {
        val cursor = MatrixCursor(CursorMapper.DEFAULT_DOCUMENT_PROJECTION)
        val entity = DriveFileEntity(
            fileId = "file123",
            accountId = "user@example.com",
            name = "photo.jpg",
            mimeType = "image/jpeg",
            size = 2048576L,
            lastModified = 1700000000000L,
            parentId = "root",
            trashed = false,
            thumbnailLink = "https://lh3.googleusercontent.com/thumb",
            webContentLink = "https://drive.google.com/content",
            webViewLink = "https://drive.google.com/view",
            canEdit = true,
            canDelete = true,
            canRename = true,
            canMoveFrom = true,
            canMoveTo = false,
            canAddChildren = false,
            exportMimeType = null,
            cachedAt = System.currentTimeMillis()
        )

        CursorMapper.addFileRow(cursor, entity, "user@example.com")
        assertEquals(1, cursor.count)

        cursor.moveToFirst()
        assertEquals("user@example.com:file123", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)))
        assertEquals("photo.jpg", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)))
        assertEquals("image/jpeg", cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
        assertEquals(2048576L, cursor.getLong(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)))

        val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS))
        assertTrue((flags and Document.FLAG_SUPPORTS_WRITE) != 0)
        assertTrue((flags and Document.FLAG_SUPPORTS_DELETE) != 0)
        assertTrue((flags and Document.FLAG_SUPPORTS_RENAME) != 0)
        assertTrue((flags and Document.FLAG_SUPPORTS_THUMBNAIL) != 0)
        assertTrue((flags and Document.FLAG_SUPPORTS_COPY) != 0)
    }

    @Test
    fun testAddFileRow_folder() {
        val cursor = MatrixCursor(CursorMapper.DEFAULT_DOCUMENT_PROJECTION)
        val folderEntity = DriveFileEntity(
            fileId = "folder999",
            accountId = "user@example.com",
            name = "Documents",
            mimeType = DriveApiConstants.MIME_GOOGLE_FOLDER,
            size = 0L,
            lastModified = 1700000000000L,
            parentId = "root",
            trashed = false,
            thumbnailLink = null,
            webContentLink = null,
            webViewLink = null,
            canEdit = true,
            canDelete = true,
            canRename = true,
            canMoveFrom = true,
            canMoveTo = true,
            canAddChildren = true,
            exportMimeType = null,
            cachedAt = System.currentTimeMillis()
        )

        CursorMapper.addFileRow(cursor, folderEntity, "user@example.com")
        cursor.moveToFirst()

        assertEquals(Document.MIME_TYPE_DIR, cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
        val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS))
        assertTrue((flags and Document.FLAG_DIR_SUPPORTS_CREATE) != 0)
    }

    @Test
    fun testAddFileRow_googleDocsExportMime() {
        val cursor = MatrixCursor(CursorMapper.DEFAULT_DOCUMENT_PROJECTION)
        val docEntity = DriveFileEntity(
            fileId = "doc123",
            accountId = "user@example.com",
            name = "Spreadsheet",
            mimeType = DriveApiConstants.MIME_GOOGLE_SPREADSHEET,
            size = 0L,
            lastModified = 1700000000000L,
            parentId = "root",
            trashed = false,
            thumbnailLink = null,
            webContentLink = null,
            webViewLink = null,
            canEdit = true,
            canDelete = true,
            canRename = true,
            canMoveFrom = true,
            canMoveTo = false,
            canAddChildren = false,
            exportMimeType = DriveApiConstants.MIME_XLSX,
            cachedAt = System.currentTimeMillis()
        )

        CursorMapper.addFileRow(cursor, docEntity, "user@example.com")
        cursor.moveToFirst()

        // Google Docs should be mapped to export MIME type (XLSX)
        assertEquals(DriveApiConstants.MIME_XLSX, cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)))
        val flags = cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_FLAGS))
        // Google docs should be read-only in SAF (no write flag)
        assertFalse((flags and Document.FLAG_SUPPORTS_WRITE) != 0)
    }
}
