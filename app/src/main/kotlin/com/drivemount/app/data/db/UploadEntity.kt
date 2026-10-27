package com.drivemount.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a pending, in-progress, or completed file upload.
 *
 * Stored in the `uploads` table with indices on `accountId` for per-account
 * queries and `status` for quick filtering of actionable uploads.
 *
 * @property uploadId Unique upload identifier (UUID).
 * @property accountId Email of the account performing the upload.
 * @property fileId Drive file ID for updates, or null for new file uploads.
 * @property fileName Display name of the file being uploaded.
 * @property parentId ID of the destination parent folder.
 * @property mimeType MIME type of the file content.
 * @property localFilePath Absolute path to the local source file.
 * @property totalSize Total size of the file in bytes.
 * @property uploadedBytes Number of bytes successfully uploaded so far.
 * @property resumableUri Google Drive resumable upload URI, or null if not yet initiated.
 * @property status Current upload status: PENDING, IN_PROGRESS, PAUSED, FAILED, or COMPLETED.
 * @property createdAt Epoch millis when this upload was enqueued.
 * @property updatedAt Epoch millis when this upload was last updated.
 * @property retryCount Number of retry attempts made so far.
 * @property errorMessage Human-readable error message if the upload failed, or null.
 */
@Entity(
    tableName = "uploads",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["status"]),
    ],
)
data class UploadEntity(
    @PrimaryKey
    @ColumnInfo(name = "uploadId")
    val uploadId: String,

    @ColumnInfo(name = "accountId")
    val accountId: String,

    @ColumnInfo(name = "fileId")
    val fileId: String?,

    @ColumnInfo(name = "fileName")
    val fileName: String,

    @ColumnInfo(name = "parentId")
    val parentId: String,

    @ColumnInfo(name = "mimeType")
    val mimeType: String,

    @ColumnInfo(name = "localFilePath")
    val localFilePath: String,

    @ColumnInfo(name = "totalSize")
    val totalSize: Long,

    @ColumnInfo(name = "uploadedBytes")
    val uploadedBytes: Long,

    @ColumnInfo(name = "resumableUri")
    val resumableUri: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,

    @ColumnInfo(name = "retryCount")
    val retryCount: Int,

    @ColumnInfo(name = "errorMessage")
    val errorMessage: String?,
) {
    companion object {
        /** Upload is queued and waiting to start. */
        const val STATUS_PENDING = "PENDING"

        /** Upload is actively sending data. */
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"

        /** Upload has been paused by the user. */
        const val STATUS_PAUSED = "PAUSED"

        /** Upload has failed after exhausting retries. */
        const val STATUS_FAILED = "FAILED"

        /** Upload has completed successfully. */
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
