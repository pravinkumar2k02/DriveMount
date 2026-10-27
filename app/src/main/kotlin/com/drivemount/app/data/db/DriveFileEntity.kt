package com.drivemount.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached Google Drive file or folder.
 *
 * Stored in the `drive_files` table with indices on `(accountId, parentId)`
 * for fast directory listing queries and `(accountId, name)` for search.
 *
 * @property fileId Google Drive's opaque file ID (primary key).
 * @property accountId Email of the account that owns or has access to this file.
 * @property name Display name of the file or folder.
 * @property mimeType MIME type of the file (including Google Workspace types).
 * @property size File size in bytes (0 for Google Workspace types / folders).
 * @property lastModified Last modification timestamp in epoch milliseconds.
 * @property parentId ID of the parent folder, or null for orphaned files.
 * @property trashed Whether the file is in the trash.
 * @property thumbnailLink Short-lived thumbnail URL.
 * @property webContentLink Direct download URL (null for Google Workspace types).
 * @property webViewLink URL for opening the file in a browser.
 * @property canEdit Whether the current user can edit this file.
 * @property canDelete Whether the current user can delete this file.
 * @property canRename Whether the current user can rename this file.
 * @property canMoveFrom Whether the current user can move this file out of its parent.
 * @property canMoveTo Whether the current user can move items into this folder.
 * @property canAddChildren Whether children can be added to this folder.
 * @property exportMimeType The preferred export MIME type for Google Workspace files, or null.
 * @property cachedAt Epoch milliseconds when this entity was last written to the cache.
 */
@Entity(
    tableName = "drive_files",
    indices = [
        Index(value = ["accountId", "parentId"]),
        Index(value = ["accountId", "name"]),
    ],
)
data class DriveFileEntity(
    @PrimaryKey
    @ColumnInfo(name = "fileId")
    val fileId: String,

    @ColumnInfo(name = "accountId")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "mimeType")
    val mimeType: String,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "lastModified")
    val lastModified: Long,

    @ColumnInfo(name = "parentId")
    val parentId: String?,

    @ColumnInfo(name = "trashed")
    val trashed: Boolean,

    @ColumnInfo(name = "thumbnailLink")
    val thumbnailLink: String?,

    @ColumnInfo(name = "webContentLink")
    val webContentLink: String?,

    @ColumnInfo(name = "webViewLink")
    val webViewLink: String?,

    @ColumnInfo(name = "canEdit")
    val canEdit: Boolean,

    @ColumnInfo(name = "canDelete")
    val canDelete: Boolean,

    @ColumnInfo(name = "canRename")
    val canRename: Boolean,

    @ColumnInfo(name = "canMoveFrom")
    val canMoveFrom: Boolean,

    @ColumnInfo(name = "canMoveTo")
    val canMoveTo: Boolean,

    @ColumnInfo(name = "canAddChildren")
    val canAddChildren: Boolean,

    @ColumnInfo(name = "exportMimeType")
    val exportMimeType: String?,

    @ColumnInfo(name = "cachedAt")
    val cachedAt: Long,
)
