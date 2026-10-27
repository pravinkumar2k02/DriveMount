package com.drivemount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for [UploadEntity] operations.
 *
 * Provides reactive observation of upload lists and mutation
 * methods for managing the upload lifecycle.
 */
@Dao
interface UploadDao {

    /**
     * Observes all uploads regardless of account or status.
     *
     * @return A [Flow] emitting the full upload list whenever it changes.
     */
    @Query("SELECT * FROM uploads ORDER BY createdAt DESC")
    fun getAllUploads(): Flow<List<UploadEntity>>

    /**
     * Observes all uploads for a specific account.
     *
     * @param accountId The account email.
     * @return A [Flow] emitting the account's upload list whenever it changes.
     */
    @Query("SELECT * FROM uploads WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun getUploadsByAccount(accountId: String): Flow<List<UploadEntity>>

    /**
     * Retrieves all pending uploads synchronously.
     *
     * Used by the upload worker to find work to process.
     *
     * @return List of uploads with status [UploadEntity.STATUS_PENDING].
     */
    @Query("SELECT * FROM uploads WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingUploads(): List<UploadEntity>

    /**
     * Retrieves a single upload by its ID.
     *
     * @param uploadId The upload UUID.
     * @return The [UploadEntity] or null if not found.
     */
    @Query("SELECT * FROM uploads WHERE uploadId = :uploadId LIMIT 1")
    suspend fun getUpload(uploadId: String): UploadEntity?

    /**
     * Inserts a new upload entity.
     *
     * @param upload The upload to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: UploadEntity)

    /**
     * Updates the progress of an in-flight upload.
     *
     * @param uploadId The upload UUID.
     * @param uploadedBytes Number of bytes uploaded so far.
     * @param resumableUri The current resumable upload URI.
     */
    @Query(
        """
        UPDATE uploads 
        SET uploadedBytes = :uploadedBytes, 
            resumableUri = :resumableUri,
            updatedAt = :uploadedBytes 
        WHERE uploadId = :uploadId
        """
    )
    suspend fun updateProgress(uploadId: String, uploadedBytes: Long, resumableUri: String?)

    /**
     * Updates the status and optional error message of an upload.
     *
     * @param uploadId The upload UUID.
     * @param status The new status string.
     * @param errorMessage An error description, or null if no error.
     */
    @Query(
        """
        UPDATE uploads 
        SET status = :status, 
            errorMessage = :errorMessage,
            updatedAt = :updatedAt 
        WHERE uploadId = :uploadId
        """
    )
    suspend fun updateStatus(
        uploadId: String,
        status: String,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Deletes a single upload by ID.
     *
     * @param uploadId The upload UUID to remove.
     */
    @Query("DELETE FROM uploads WHERE uploadId = :uploadId")
    suspend fun delete(uploadId: String)

    /**
     * Deletes all completed uploads.
     */
    @Query("DELETE FROM uploads WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()
}
