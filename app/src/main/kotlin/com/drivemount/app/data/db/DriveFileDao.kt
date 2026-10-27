package com.drivemount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for [DriveFileEntity] operations.
 *
 * Provides reactive [Flow]-based queries for directory listings and search,
 * along with synchronous accessors for use in background workers and
 * content providers.
 */
@Dao
interface DriveFileDao {

    /**
     * Observes all non-trashed files within a specific parent folder for a given account.
     *
     * @param accountId The account email.
     * @param parentId The parent folder ID.
     * @return A [Flow] emitting the list of child files whenever it changes.
     */
    @Query(
        """
        SELECT * FROM drive_files 
        WHERE accountId = :accountId 
          AND parentId = :parentId 
          AND trashed = 0 
        ORDER BY 
          CASE WHEN mimeType = 'application/vnd.google-apps.folder' THEN 0 ELSE 1 END,
          name COLLATE NOCASE ASC
        """
    )
    fun getFilesByParent(accountId: String, parentId: String): Flow<List<DriveFileEntity>>

    /**
     * Retrieves a single file entity by its file ID (suspending).
     *
     * @param fileId The Google Drive file ID.
     * @return The [DriveFileEntity] or null if not cached.
     */
    @Query("SELECT * FROM drive_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getFile(fileId: String): DriveFileEntity?

    /**
     * Retrieves a single file entity by its file ID (blocking).
     *
     * Suitable for content providers and other non-coroutine contexts.
     *
     * @param fileId The Google Drive file ID.
     * @return The [DriveFileEntity] or null if not cached.
     */
    @Query("SELECT * FROM drive_files WHERE fileId = :fileId LIMIT 1")
    fun getFileSync(fileId: String): DriveFileEntity?

    /**
     * Searches cached files by name for a given account.
     *
     * @param accountId The account email.
     * @param query The search term (matched with SQL LIKE).
     * @return A [Flow] emitting matching files whenever the data changes.
     */
    @Query(
        """
        SELECT * FROM drive_files 
        WHERE accountId = :accountId 
          AND name LIKE '%' || :query || '%' 
          AND trashed = 0 
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun searchFiles(accountId: String, query: String): Flow<List<DriveFileEntity>>

    /**
     * Inserts or replaces a batch of file entities.
     *
     * @param files The list of entities to upsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<DriveFileEntity>)

    /**
     * Inserts or replaces a single file entity.
     *
     * @param file The entity to upsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: DriveFileEntity)

    /**
     * Deletes a cached file by its file ID.
     *
     * @param fileId The Google Drive file ID to remove.
     */
    @Query("DELETE FROM drive_files WHERE fileId = :fileId")
    suspend fun deleteByFileId(fileId: String)

    /**
     * Deletes all cached files belonging to a specific account.
     *
     * Used when an account is removed.
     *
     * @param accountId The account email.
     */
    @Query("DELETE FROM drive_files WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: String)

    /**
     * Deletes all cached files within a specific parent folder for an account.
     *
     * Used before refreshing a directory listing to avoid stale entries.
     *
     * @param accountId The account email.
     * @param parentId The parent folder ID.
     */
    @Query("DELETE FROM drive_files WHERE accountId = :accountId AND parentId = :parentId")
    suspend fun deleteByParent(accountId: String, parentId: String)

    /**
     * Checks whether [childId] is a descendant of [parentId] using a
     * recursive Common Table Expression (CTE).
     *
     * This walks up the parent chain from [childId] to see if it
     * eventually reaches [parentId].
     *
     * @param parentId The ancestor folder ID to check.
     * @param childId The potential descendant file ID.
     * @return `true` if [childId] is a child (at any depth) of [parentId].
     */
    @Query(
        """
        WITH RECURSIVE ancestors(id) AS (
            SELECT parentId FROM drive_files WHERE fileId = :childId
            UNION ALL
            SELECT df.parentId FROM drive_files df 
            INNER JOIN ancestors a ON df.fileId = a.id
            WHERE df.parentId IS NOT NULL
        )
        SELECT EXISTS(SELECT 1 FROM ancestors WHERE id = :parentId)
        """
    )
    suspend fun isChild(parentId: String, childId: String): Boolean

    /**
     * Retrieves all files for an account that were modified after a given timestamp.
     *
     * @param accountId The account email.
     * @param timestamp Epoch millis threshold.
     * @return List of [DriveFileEntity] modified after [timestamp].
     */
    @Query(
        """
        SELECT * FROM drive_files 
        WHERE accountId = :accountId 
          AND lastModified > :timestamp 
        ORDER BY lastModified DESC
        """
    )
    suspend fun getFilesModifiedAfter(accountId: String, timestamp: Long): List<DriveFileEntity>

    /**
     * Returns the total number of cached file rows.
     *
     * @return The row count.
     */
    @Query("SELECT COUNT(*) FROM drive_files")
    suspend fun getCacheSize(): Long

    /**
     * Deletes the oldest cached entries for an account, ordered by [DriveFileEntity.cachedAt].
     *
     * @param accountId The account email.
     * @param limit Maximum number of rows to delete.
     */
    @Query(
        """
        DELETE FROM drive_files 
        WHERE fileId IN (
            SELECT fileId FROM drive_files 
            WHERE accountId = :accountId 
            ORDER BY cachedAt ASC 
            LIMIT :limit
        )
        """
    )
    suspend fun deleteOldest(accountId: String, limit: Int)
}
