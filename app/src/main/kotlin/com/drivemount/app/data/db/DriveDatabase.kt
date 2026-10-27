package com.drivemount.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the DriveMount application.
 *
 * Contains three entity tables:
 * - [DriveFileEntity] — cached Google Drive file and folder metadata.
 * - [AccountEntity] — signed-in Google account information and tokens.
 * - [UploadEntity] — pending and in-progress file upload records.
 *
 * Version 1: initial schema.
 */
@Database(
    entities = [
        DriveFileEntity::class,
        AccountEntity::class,
        UploadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DriveDatabase : RoomDatabase() {

    /** Provides access to cached Drive file operations. */
    abstract fun driveFileDao(): DriveFileDao

    /** Provides access to account operations. */
    abstract fun accountDao(): AccountDao

    /** Provides access to upload operations. */
    abstract fun uploadDao(): UploadDao

    companion object {
        /** The database file name on disk. */
        const val DATABASE_NAME = "drivemount.db"
    }
}
