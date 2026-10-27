package com.drivemount.app.data.repository

import com.drivemount.app.data.api.DriveApiClient
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.api.DriveAbout
import com.drivemount.app.data.cache.BlockCache
import com.drivemount.app.data.db.AccountDao
import com.drivemount.app.data.db.DriveFileDao
import com.drivemount.app.data.db.DriveFileEntity
import com.drivemount.app.data.db.UploadDao
import com.drivemount.app.data.db.UploadEntity
import com.drivemount.app.util.Constants
import com.drivemount.app.util.generateUploadId
import com.drivemount.app.util.isGoogleDocsType
import com.drivemount.app.util.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth repository for Google Drive data.
 *
 * Coordinates between the Room SQLite cache, disk block cache, and Google Drive REST API v3.
 */
@Singleton
class DriveRepository @Inject constructor(
    private val apiClient: DriveApiClient,
    private val driveFileDao: DriveFileDao,
    private val accountDao: AccountDao,
    private val uploadDao: UploadDao,
    private val blockCache: BlockCache,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Returns an observable [Flow] of files within a parent folder.
     * Automatically triggers a background refresh if data is missing or stale.
     */
    fun listChildren(
        accountId: String,
        parentId: String,
        forceRefresh: Boolean = false,
    ): Flow<List<DriveFileEntity>> {
        if (forceRefresh) {
            repositoryScope.launch {
                try {
                    refreshChildren(accountId, parentId)
                } catch (e: Exception) {
                    Timber.w(e, "Background refresh failed for parent %s", parentId)
                }
            }
        }
        return driveFileDao.getFilesByParent(accountId, parentId)
    }

    /**
     * Refreshes cached children of a folder directly from the Drive API.
     */
    suspend fun refreshChildren(accountId: String, parentId: String) = withContext(Dispatchers.IO) {
        Timber.d("Refreshing folder %s for account %s...", parentId, accountId)
        val allFiles = mutableListOf<DriveFileEntity>()
        var pageToken: String? = null

        do {
            val response = apiClient.listFiles(
                accountId = accountId,
                parentId = parentId,
                pageToken = pageToken
            )
            val entities = response.files.map { it.toEntity(accountId) }
            allFiles.addAll(entities)
            pageToken = response.nextPageToken
        } while (pageToken != null)

        driveFileDao.deleteByParent(accountId, parentId)
        driveFileDao.insertAll(allFiles)
        Timber.d("Successfully refreshed %d children for folder %s", allFiles.size, parentId)
    }

    /**
     * Retrieves file metadata, checking Room first and falling back to the API.
     */
    suspend fun getFile(accountId: String, fileId: String): DriveFileEntity? = withContext(Dispatchers.IO) {
        val cached = driveFileDao.getFile(fileId)
        if (cached != null) return@withContext cached

        return@withContext try {
            val file = apiClient.getFile(accountId, fileId)
            val entity = file.toEntity(accountId)
            driveFileDao.insert(entity)
            entity
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file %s", fileId)
            null
        }
    }

    /**
     * Reads a 1 MB block from the LRU block cache or downloads it via HTTP Range request.
     */
    suspend fun readBlock(
        accountId: String,
        fileId: String,
        blockOffset: Long,
        fileSize: Long,
    ): ByteArray = withContext(Dispatchers.IO) {
        val cached = blockCache.get(accountId, fileId, blockOffset)
        if (cached != null) {
            return@withContext cached
        }

        val blockSize = Constants.BLOCK_SIZE
        val start = blockOffset * blockSize
        val end = (start + blockSize - 1L).coerceAtMost(fileSize - 1L)

        val data = apiClient.downloadRange(accountId, fileId, start, end)
        blockCache.put(accountId, fileId, blockOffset, data)
        data
    }

    /**
     * Fetches current account storage quota information from Drive API and updates Room.
     */
    suspend fun getAbout(accountId: String): DriveAbout = withContext(Dispatchers.IO) {
        val about = apiClient.getAbout(accountId)
        val limit = about.storageQuota.limit ?: (15L * 1024L * 1024L * 1024L)
        val usage = about.storageQuota.usage ?: 0L
        accountDao.updateStorageQuota(accountId, limit, usage)
        about
    }

    /**
     * Creates a new document or folder in Google Drive and caches it in Room.
     */
    suspend fun createDocument(
        accountId: String,
        parentId: String,
        mimeType: String,
        displayName: String,
    ): DriveFileEntity = withContext(Dispatchers.IO) {
        val createdFile = if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR ||
            mimeType == DriveApiConstants.MIME_GOOGLE_FOLDER
        ) {
            apiClient.createFolder(accountId, displayName, parentId)
        } else {
            apiClient.createFile(accountId, displayName, parentId, mimeType)
        }

        val entity = createdFile.toEntity(accountId)
        driveFileDao.insert(entity)
        entity
    }

    /**
     * Deletes (trashes) a document from Google Drive and removes it from Room.
     */
    suspend fun deleteDocument(accountId: String, fileId: String) = withContext(Dispatchers.IO) {
        apiClient.deleteFile(accountId, fileId)
        driveFileDao.deleteByFileId(fileId)
        blockCache.evictFile(accountId, fileId)
    }

    /**
     * Renames a document in Google Drive.
     */
    suspend fun renameDocument(
        accountId: String,
        fileId: String,
        newName: String,
    ): DriveFileEntity = withContext(Dispatchers.IO) {
        val updated = apiClient.renameFile(accountId, fileId, newName)
        val entity = updated.toEntity(accountId)
        driveFileDao.insert(entity)
        entity
    }

    /**
     * Moves a document to a different parent folder.
     */
    suspend fun moveDocument(
        accountId: String,
        fileId: String,
        newParentId: String,
        oldParentId: String,
    ): DriveFileEntity = withContext(Dispatchers.IO) {
        val moved = apiClient.moveFile(accountId, fileId, newParentId, oldParentId)
        val entity = moved.toEntity(accountId)
        driveFileDao.insert(entity)
        entity
    }

    /**
     * Creates a copy of an existing document in Google Drive.
     */
    suspend fun copyDocument(
        accountId: String,
        fileId: String,
        newParentId: String,
        newName: String,
    ): DriveFileEntity = withContext(Dispatchers.IO) {
        val copy = apiClient.copyFile(accountId, fileId, newParentId, newName)
        val entity = copy.toEntity(accountId)
        driveFileDao.insert(entity)
        entity
    }

    /**
     * Enqueues a local file for resumable upload to Google Drive.
     */
    suspend fun enqueueUpload(
        accountId: String,
        fileName: String,
        parentId: String,
        mimeType: String,
        localFilePath: String,
        fileSize: Long,
    ): String = withContext(Dispatchers.IO) {
        val uploadId = generateUploadId()
        val uploadEntity = UploadEntity(
            uploadId = uploadId,
            accountId = accountId,
            fileId = null,
            fileName = fileName,
            parentId = parentId,
            mimeType = mimeType,
            localFilePath = localFilePath,
            totalSize = fileSize,
            uploadedBytes = 0L,
            resumableUri = null,
            status = UploadEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            retryCount = 0,
            errorMessage = null
        )
        uploadDao.insert(uploadEntity)
        Timber.i("Enqueued upload %s for %s (%d bytes)", uploadId, fileName, fileSize)
        uploadId
    }

    /**
     * Searches cached files by name query.
     */
    fun searchFiles(accountId: String, query: String): Flow<List<DriveFileEntity>> {
        return driveFileDao.searchFiles(accountId, query)
    }

    /**
     * Performs an incremental sync of changes from Google Drive Changes API.
     */
    suspend fun syncChanges(accountId: String) = withContext(Dispatchers.IO) {
        val account = accountDao.getAccount(accountId) ?: return@withContext
        var pageToken = account.changesPageToken

        if (pageToken == null) {
            val startToken = apiClient.getChangesStartPageToken(accountId)
            accountDao.updateChangesPageToken(accountId, startToken)
            return@withContext
        }

        try {
            var changesList = apiClient.getChanges(accountId, pageToken)
            for (change in changesList.changes) {
                val fileId = change.fileId ?: continue
                if (change.removed || (change.file?.trashed == true)) {
                    driveFileDao.deleteByFileId(fileId)
                    blockCache.evictFile(accountId, fileId)
                } else if (change.file != null) {
                    driveFileDao.insert(change.file.toEntity(accountId))
                }
            }

            val nextToken = changesList.newStartPageToken ?: changesList.nextPageToken
            if (nextToken != null) {
                accountDao.updateChangesPageToken(accountId, nextToken)
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync changes failed for account %s", accountId)
        }
    }

    /**
     * Fetches raw thumbnail image bytes from Google Drive API.
     */
    suspend fun getThumbnailBytes(accountId: String, thumbnailUrl: String): ByteArray = withContext(Dispatchers.IO) {
        apiClient.getThumbnail(accountId, thumbnailUrl)
    }

    /**
     * Exports a Google Workspace document into the specified export format.
     */
    suspend fun exportFile(
        accountId: String,
        fileId: String,
        exportMimeType: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        apiClient.exportFile(accountId, fileId, exportMimeType)
    }

    /**
     * Observes all active and pending uploads.
     */
    fun getAllUploads(): Flow<List<UploadEntity>> = uploadDao.getAllUploads()
}

