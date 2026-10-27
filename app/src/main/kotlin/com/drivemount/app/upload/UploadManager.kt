package com.drivemount.app.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.drivemount.app.data.db.UploadDao
import com.drivemount.app.data.db.UploadEntity
import com.drivemount.app.settings.SettingsStore
import com.drivemount.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the scheduling, pausing, resumption, and cancellation of background uploads.
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val uploadDao: UploadDao,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    /**
     * Enqueues an upload job in WorkManager with proper constraints.
     */
    fun enqueueUpload(uploadId: String, accountId: String, fileName: String) {
        scope.launch {
            val wifiOnly = settingsStore.wifiOnlyUploads.first()
            val chargingOnly = settingsStore.chargingOnlyUploads.first()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(chargingOnly)
                .build()

            val inputData = Data.Builder()
                .putString(UploadWorker.KEY_UPLOAD_ID, uploadId)
                .putString(UploadWorker.KEY_ACCOUNT_ID, accountId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Constants.INITIAL_BACKOFF_MS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("upload_$uploadId")
                .build()

            workManager.enqueueUniqueWork(
                "upload_$uploadId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Timber.d("Enqueued WorkManager task for upload: %s (%s)", uploadId, fileName)
        }
    }

    /**
     * Pauses an active upload.
     */
    fun pauseUpload(uploadId: String) {
        scope.launch {
            workManager.cancelUniqueWork("upload_$uploadId")
            uploadDao.updateStatus(uploadId, UploadEntity.STATUS_PAUSED, null)
            Timber.d("Paused upload %s", uploadId)
        }
    }

    /**
     * Resumes a paused or failed upload.
     */
    fun resumeUpload(uploadId: String) {
        scope.launch {
            val upload = uploadDao.getUpload(uploadId) ?: return@launch
            uploadDao.updateStatus(uploadId, UploadEntity.STATUS_PENDING, null)
            enqueueUpload(uploadId, upload.accountId, upload.fileName)
            Timber.d("Resumed upload %s", uploadId)
        }
    }

    /**
     * Cancels an upload, cancels background work, and cleans up local temp files.
     */
    fun cancelUpload(uploadId: String) {
        scope.launch {
            workManager.cancelUniqueWork("upload_$uploadId")
            val upload = uploadDao.getUpload(uploadId)
            if (upload != null) {
                val file = File(upload.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                uploadDao.delete(uploadId)
            }
            Timber.d("Cancelled upload %s", uploadId)
        }
    }

    /**
     * Creates the persistent notification channel for upload progress.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "File Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file uploads to Google Drive"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

