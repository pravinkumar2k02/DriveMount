package com.drivemount.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a signed-in Google account.
 *
 * Stored in the `accounts` table. The [accountId] is the user's email address
 * and serves as the primary key.
 *
 * @property accountId Email address of the Google account (primary key).
 * @property displayName User's display name from their Google profile.
 * @property photoUrl URL to the user's profile photo, or null.
 * @property refreshToken Encrypted OAuth2 refresh token.
 * @property accessToken Current OAuth2 access token.
 * @property tokenExpiry Epoch millis when [accessToken] expires.
 * @property changesPageToken The last-seen changes page token for delta sync, or null.
 * @property storageLimit Total Drive storage limit in bytes.
 * @property storageUsed Total Drive storage used in bytes.
 * @property lastSyncedAt Epoch millis when this account was last fully synced.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "accountId")
    val accountId: String,

    @ColumnInfo(name = "displayName")
    val displayName: String,

    @ColumnInfo(name = "photoUrl")
    val photoUrl: String?,

    @ColumnInfo(name = "refreshToken")
    val refreshToken: String,

    @ColumnInfo(name = "accessToken")
    val accessToken: String,

    @ColumnInfo(name = "tokenExpiry")
    val tokenExpiry: Long,

    @ColumnInfo(name = "changesPageToken")
    val changesPageToken: String?,

    @ColumnInfo(name = "storageLimit")
    val storageLimit: Long,

    @ColumnInfo(name = "storageUsed")
    val storageUsed: Long,

    @ColumnInfo(name = "lastSyncedAt")
    val lastSyncedAt: Long,
)
