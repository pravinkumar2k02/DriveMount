package com.drivemount.app.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.drivemount.app.R
import com.drivemount.app.data.api.DriveApiClient
import com.drivemount.app.data.api.DriveApiConstants
import com.drivemount.app.data.db.UploadDao
import com.drivemount.app.data.db.UploadEntity
import com.drivemount.app.provider.CursorMapper
import com.drivemount.app.settings.SettingsStore
import com.drivemount.app.util.Constants
import com.drivemount.app.util.isNetworkUnmetered
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Background worker that performs resumable multi-chunk file uploads to Google Drive.
 *
 * Implements Google Drive's resumable upload protocol using 8 MB chunks. Handles network drops,
 * resumes from the last byte received by Google's servers, updates Room progress, and displays
 * a persistent foreground notification.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: DriveApiClient,
    private val uploadDao: UploadDao,
    private val settingsStore: SettingsStore,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_UPLOAD_ID = "key_upload_id"
        const val KEY_ACCOUNT_ID = "key_account_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString(KEY_UPLOAD_ID) ?: return Result.failure()
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()

        var upload = uploadDao.getUpload(uploadId) ?: return Result.failure()

        // Verify Wi-Fi requirement if configured
        val wifiOnly = settingsStore.wifiOnlyUploads.first()
        if (wifiOnly && !context.isNetworkUnmetered()) {
            Timber.d("Upload %s deferred: waiting for Wi-Fi network.", uploadId)
            return Result.retry()
        }

        // Show foreground notification
        try {
            setForeground(createForegroundInfo(upload.fileName, 0, upload.totalSize))
        } catch (e: Exception) {
            Timber.w(e, "Could not set foreground info")
        }

        uploadDao.updateStatus(uploadId, UploadEntity.STATUS_IN_PROGRESS, null)

        val localFile = File(upload.localFilePath)
        if (!localFile.exists()) {
            val errorMsg = "Local file not found: ${upload.localFilePath}"
            Timber.e(errorMsg)
            uploadDao.updateStatus(uploadId, UploadEntity.STATUS_FAILED, errorMsg)
            return Result.failure()
        }

        val totalSize = localFile.length()

        return try {
            // 1. Obtain or resume resumable upload URI
            var uploadUri = upload.resumableUri
            var uploadedBytes = 0L

            if (uploadUri.isNullOrBlank()) {
                uploadUri = apiClient.initiateResumableUpload(
                    accountId = accountId,
                    fileId = upload.fileId,
                    name = upload.fileName,
                    parentId = upload.parentId,
                    mimeType = upload.mimeType,
                    fileSize = totalSize
                )
                uploadDao.updateProgress(uploadId, 0L, uploadUri)
            } else {
                // Query server for resume offset
                uploadedBytes = try {
                    apiClient.getUploadProgress(uploadUri)
                } catch (e: Exception) {
                    Timber.w(e, "Could not query upload progress; restarting from 0")
                    0L
                }
            }

            // 2. Stream remaining chunks
            val chunkSize = DriveApiConstants.UPLOAD_CHUNK_SIZE
            val buffer = ByteArray(chunkSize.toInt())

            FileInputStream(localFile).use { fis ->
                if (uploadedBytes > 0) {
                    fis.skip(uploadedBytes)
                }

                while (uploadedBytes < totalSize) {
                    if (isStopped) {
                        Timber.d("Upload %s stopped/cancelled by WorkManager", uploadId)
                        uploadDao.updateStatus(uploadId, UploadEntity.STATUS_PAUSED, null)
                        return Result.retry()
                    }

                    val bytesToRead = ((totalSize - uploadedBytes).coerceAtMost(chunkSize)).toInt()
                    var bytesReadThisChunk = 0
                    while (bytesReadThisChunk < bytesToRead) {
                        val read = fis.read(buffer, bytesReadThisChunk, bytesToRead - bytesReadThisChunk)
                        if (read == -1) break
                        bytesReadThisChunk += read
                    }

                    if (bytesReadThisChunk == 0) break

                    val chunkData = if (bytesReadThisChunk == buffer.size) {
                        buffer
                    } else {
                        buffer.copyOf(bytesReadThisChunk)
                    }

                    val isComplete = apiClient.uploadChunk(
                        uploadUri = uploadUri,
                        chunk = chunkData,
                        offset = uploadedBytes,
                        totalSize = totalSize
                    )

                    uploadedBytes += bytesReadThisChunk
                    uploadDao.updateProgress(uploadId, uploadedBytes, uploadUri)

                    // Update notification
                    val progressPercent = if (totalSize > 0) ((uploadedBytes * 100) / totalSize).toInt() else 0
                    notificationManager.notify(
                        Constants.NOTIFICATION_ID,
                        buildNotification(upload.fileName, progressPercent, totalSize)
                    )

                    if (isComplete) break
                }
            }

            // 3. Mark completed and cleanup
            uploadDao.updateStatus(uploadId, UploadEntity.STATUS_COMPLETED, null)
            localFile.delete()

            // Notify ContentResolver to refresh directory listings
            val parentDocId = CursorMapper.buildDocumentId(accountId, upload.parentId)
            val childDocsUri = DocumentsContract.buildChildDocumentsUri(Constants.AUTHORITY, parentDocId)
            context.contentResolver.notifyChange(childDocsUri, null)

            notificationManager.cancel(Constants.NOTIFICATION_ID)
            Timber.i("Upload %s (%s) completed successfully!", uploadId, upload.fileName)
            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Upload %s encountered an error", uploadId)
            val currentRetry = runAttemptCount
            if (currentRetry < Constants.MAX_RETRY_COUNT) {
                uploadDao.updateStatus(uploadId, UploadEntity.STATUS_IN_PROGRESS, "Retrying (${currentRetry + 1}/${Constants.MAX_RETRY_COUNT})...")
                Result.retry()
            } else {
                uploadDao.updateStatus(uploadId, UploadEntity.STATUS_FAILED, e.localizedMessage ?: "Upload failed")
                notificationManager.cancel(Constants.NOTIFICATION_ID)
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(fileName: String, progress: Int, totalSize: Long): ForegroundInfo {
        ensureNotificationChannel()
        val notification = buildNotification(fileName, progress, totalSize)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(Constants.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(fileName: String, progress: Int, totalSize: Long): Notification {
        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Uploading to Google Drive")
            .setContentText("$fileName ($progress%)")
            .setSmallIcon(R.drawable.ic_drive)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "File Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file uploads to Google Drive"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}

