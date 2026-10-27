package com.drivemount.app.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.drivemount.app.R
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.cache.BlockCache
import com.drivemount.app.data.db.AccountDao
import com.drivemount.app.data.db.DriveFileDao
import com.drivemount.app.data.db.DriveFileEntity
import com.drivemount.app.data.repository.DriveRepository
import com.drivemount.app.upload.UploadManager
import com.drivemount.app.util.Constants
import com.drivemount.app.util.isGoogleDocsType
import com.drivemount.app.util.toExportMimeType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Android [DocumentsProvider] that exposes Google Drive accounts as storage roots
 * in the system Files app, file pickers, and save dialogs via the Storage Access Framework (SAF).
 */
class DriveDocumentsProvider : DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DriveProviderEntryPoint {
        fun driveRepository(): DriveRepository
        fun accountDao(): AccountDao
        fun driveFileDao(): DriveFileDao
        fun uploadManager(): UploadManager
        fun blockCache(): BlockCache
    }

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var entryPoint: DriveProviderEntryPoint
    private lateinit var storageManager: StorageManager
    private lateinit var proxyHandlerThread: HandlerThread
    private lateinit var proxyHandler: Handler

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        entryPoint = EntryPointAccessors.fromApplication(appContext, DriveProviderEntryPoint::class.java)
        storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        proxyHandlerThread = HandlerThread("DriveProxyThread").apply { start() }
        proxyHandler = Handler(proxyHandlerThread.looper)
        Timber.i("DriveDocumentsProvider initialized successfully.")
        return true
    }

    // ── queryRoots ──────────────────────────────────────────────────────────

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(CursorMapper.resolveProjection(projection, CursorMapper.DEFAULT_ROOT_PROJECTION))
        val accounts = entryPoint.accountDao().getAllAccountsSync()

        for (account in accounts) {
            val rootId = account.accountId
            val rootDocId = CursorMapper.buildDocumentId(account.accountId, "root")
            val availableBytes = (account.storageLimit - account.storageUsed).coerceAtLeast(0L)

            val flags = Root.FLAG_SUPPORTS_CREATE or
                Root.FLAG_SUPPORTS_IS_CHILD or
                Root.FLAG_SUPPORTS_SEARCH or
                Root.FLAG_SUPPORTS_RECENTS

            val row = result.newRow()
            row.add(Root.COLUMN_ROOT_ID, rootId)
            row.add(Root.COLUMN_FLAGS, flags)
            row.add(Root.COLUMN_ICON, R.drawable.ic_drive)
            row.add(Root.COLUMN_TITLE, "Google Drive (${account.displayName.ifBlank { account.accountId }})")
            row.add(Root.COLUMN_SUMMARY, account.accountId)
            row.add(Root.COLUMN_DOCUMENT_ID, rootDocId)
            row.add(Root.COLUMN_AVAILABLE_BYTES, availableBytes)
            row.add(Root.COLUMN_CAPACITY_BYTES, account.storageLimit)
            row.add(Root.COLUMN_MIME_TYPES, "*/*")
        }

        return result
    }

    // ── queryDocument ───────────────────────────────────────────────────────

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val (accountId, fileId) = CursorMapper.parseDocumentId(documentId)
        val result = MatrixCursor(CursorMapper.resolveProjection(projection, CursorMapper.DEFAULT_DOCUMENT_PROJECTION))

        if (fileId == "root") {
            val row = result.newRow()
            row.add(Document.COLUMN_DOCUMENT_ID, documentId)
            row.add(Document.COLUMN_DISPLAY_NAME, "My Drive")
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            row.add(Document.COLUMN_SIZE, 0L)
            row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            row.add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
            return result
        }

        val entity = runBlocking {
            withTimeoutOrNull(5_000L) {
                entryPoint.driveRepository().getFile(accountId, fileId)
            }
        } ?: throw FileNotFoundException("Document not found: $documentId")

        CursorMapper.addFileRow(result, entity, accountId)
        return result
    }

    // ── queryChildDocuments ─────────────────────────────────────────────────

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val (accountId, parentFileId) = CursorMapper.parseDocumentId(parentDocumentId)
        val result = MatrixCursor(CursorMapper.resolveProjection(projection, CursorMapper.DEFAULT_DOCUMENT_PROJECTION))

        // Build notification URI for reactive UI updates
        val childDocsUri = DocumentsContract.buildChildDocumentsUri(Constants.AUTHORITY, parentDocumentId)
        result.setNotificationUri(context?.contentResolver, childDocsUri)

        // 1. Read cached files from Room synchronously with a quick timeout
        val cachedFiles = runBlocking {
            withTimeoutOrNull(3_000L) {
                entryPoint.driveFileDao().getFilesByParent(accountId, parentFileId).first()
            }
        } ?: emptyList()

        for (file in cachedFiles) {
            CursorMapper.addFileRow(result, file, accountId)
        }

        // 2. Set EXTRA_LOADING flag and trigger background refresh
        val extras = Bundle()
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, true)
        result.extras = extras

        providerScope.launch {
            try {
                entryPoint.driveRepository().refreshChildren(accountId, parentFileId)
                // Notify system Files app that refresh finished
                context?.contentResolver?.notifyChange(childDocsUri, null)
            } catch (e: Exception) {
                Timber.w(e, "Background directory refresh failed for parent %s", parentFileId)
            }
        }

        return result
    }

    // ── openDocument ────────────────────────────────────────────────────────

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (accountId, fileId) = CursorMapper.parseDocumentId(documentId)
        val isWrite = mode.contains("w") || mode.contains("W") || mode.contains("r+")

        if (!isWrite) {
            // READ mode: transparent Range-based streaming via ProxyFileDescriptor
            val entity = runBlocking {
                entryPoint.driveRepository().getFile(accountId, fileId)
            } ?: throw FileNotFoundException("File not found: $documentId")

            // Check if this is a Google Docs proprietary file (needs export)
            if (entity.mimeType.isGoogleDocsType()) {
                val exportMime = entity.exportMimeType ?: entity.mimeType.toExportMimeType() ?: "application/pdf"
                return openExportedDocument(accountId, fileId, entity.name, exportMime)
            }

            val modeBits = ParcelFileDescriptor.parseMode(mode)
            val callback = ProxyReadCallback(
                accountId = accountId,
                fileId = fileId,
                fileSize = entity.size,
                repository = entryPoint.driveRepository(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )

            return storageManager.openProxyFileDescriptor(modeBits, callback, proxyHandler)
        } else {
            // WRITE mode: Stream into local temporary cache file, then enqueue resumable upload
            return openDocumentForWrite(accountId, fileId, mode)
        }
    }

    private fun openExportedDocument(
        accountId: String,
        fileId: String,
        displayName: String,
        exportMimeType: String,
    ): ParcelFileDescriptor {
        val cacheDir = File(context?.cacheDir, "exported_docs").apply { if (!exists()) mkdirs() }
        val tempFile = File(cacheDir, "${accountId}_${fileId}_exported")

        val bytes = runBlocking {
            entryPoint.driveRepository().exportFile(accountId, fileId, exportMimeType)
        }
        tempFile.writeBytes(bytes)

        return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun openDocumentForWrite(
        accountId: String,
        fileId: String,
        mode: String,
    ): ParcelFileDescriptor {
        val cacheDir = File(context?.cacheDir, "pending_writes").apply { if (!exists()) mkdirs() }
        val tempFile = File(cacheDir, "write_${accountId}_${fileId}_${System.currentTimeMillis()}")

        // Check local disk space with StatFs
        val stat = StatFs(cacheDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        if (availableBytes < 100L * 1024L * 1024L) { // Less than 100 MB free
            throw IOException("Insufficient local disk storage for temporary write buffer")
        }

        // Reliable pipe: write end given to caller, read end piped to temp file
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        providerScope.launch {
            try {
                FileInputStream(readEnd.fileDescriptor).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                readEnd.close()

                // Enqueue upload via WorkManager
                val fileSize = tempFile.length()
                val uploadId = entryPoint.driveRepository().enqueueUpload(
                    accountId = accountId,
                    fileName = tempFile.name,
                    parentId = "root",
                    mimeType = "application/octet-stream",
                    localFilePath = tempFile.absolutePath,
                    fileSize = fileSize
                )
                entryPoint.uploadManager().enqueueUpload(uploadId, accountId, tempFile.name)
                Timber.i("Completed local write pipe for %s. Enqueued upload %s.", fileId, uploadId)

            } catch (e: Exception) {
                Timber.e(e, "Error streaming written data into temporary file")
                tempFile.delete()
            }
        }

        return writeEnd
    }

    // ── Document Mutations ──────────────────────────────────────────────────

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val (accountId, parentFileId) = CursorMapper.parseDocumentId(parentDocumentId)
        val created = runBlocking {
            entryPoint.driveRepository().createDocument(accountId, parentFileId, mimeType, displayName)
        }
        notifyChildChange(parentDocumentId)
        return CursorMapper.buildDocumentId(accountId, created.fileId)
    }

    override fun deleteDocument(documentId: String) {
        val (accountId, fileId) = CursorMapper.parseDocumentId(documentId)
        val entity = runBlocking { entryPoint.driveFileDao().getFile(fileId) }
        runBlocking {
            entryPoint.driveRepository().deleteDocument(accountId, fileId)
        }
        entity?.parentId?.let { parentId ->
            notifyChildChange(CursorMapper.buildDocumentId(accountId, parentId))
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val (accountId, fileId) = CursorMapper.parseDocumentId(documentId)
        val updated = runBlocking {
            entryPoint.driveRepository().renameDocument(accountId, fileId, displayName)
        }
        updated.parentId?.let { parentId ->
            notifyChildChange(CursorMapper.buildDocumentId(accountId, parentId))
        }
        return documentId
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val (accountId, fileId) = CursorMapper.parseDocumentId(sourceDocumentId)
        val (_, sourceParentId) = CursorMapper.parseDocumentId(sourceParentDocumentId)
        val (_, targetParentId) = CursorMapper.parseDocumentId(targetParentDocumentId)

        runBlocking {
            entryPoint.driveRepository().moveDocument(accountId, fileId, targetParentId, sourceParentId)
        }
        notifyChildChange(sourceParentDocumentId)
        notifyChildChange(targetParentDocumentId)
        return sourceDocumentId
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        val (accountId, fileId) = CursorMapper.parseDocumentId(sourceDocumentId)
        val (_, targetParentId) = CursorMapper.parseDocumentId(targetParentDocumentId)

        val entity = runBlocking { entryPoint.driveRepository().getFile(accountId, fileId) }
            ?: throw FileNotFoundException("Source document not found: $sourceDocumentId")

        val copy = runBlocking {
            entryPoint.driveRepository().copyDocument(accountId, fileId, targetParentId, entity.name)
        }
        notifyChildChange(targetParentDocumentId)
        return CursorMapper.buildDocumentId(accountId, copy.fileId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val (parentAccount, parentFileId) = CursorMapper.parseDocumentId(parentDocumentId)
        val (childAccount, childFileId) = CursorMapper.parseDocumentId(documentId)
        if (parentAccount != childAccount) return false

        return runBlocking {
            entryPoint.driveFileDao().isChild(parentFileId, childFileId)
        }
    }

    // ── Search & Thumbnails ─────────────────────────────────────────────────

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor {
        val result = MatrixCursor(CursorMapper.resolveProjection(projection, CursorMapper.DEFAULT_DOCUMENT_PROJECTION))
        val matchingFiles = runBlocking {
            entryPoint.driveFileDao().searchFiles(rootId, query).first()
        }
        for (file in matchingFiles) {
            CursorMapper.addFileRow(result, file, rootId)
        }
        return result
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        val (accountId, fileId) = CursorMapper.parseDocumentId(documentId)
        val entity = runBlocking { entryPoint.driveRepository().getFile(accountId, fileId) }
            ?: throw FileNotFoundException("Document not found: $documentId")

        val thumbnailLink = entity.thumbnailLink
            ?: throw FileNotFoundException("No thumbnail available for: $documentId")

        val thumbBytes = runBlocking {
            entryPoint.driveRepository().getThumbnailBytes(accountId, thumbnailLink)
        }

        val cacheDir = File(context?.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val tempFile = File(cacheDir, "thumb_${accountId}_${fileId}.png")
        tempFile.writeBytes(thumbBytes)

        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, tempFile.length())
    }

    private fun notifyChildChange(parentDocumentId: String) {
        val childDocsUri = DocumentsContract.buildChildDocumentsUri(Constants.AUTHORITY, parentDocumentId)
        context?.contentResolver?.notifyChange(childDocsUri, null)
    }
}

