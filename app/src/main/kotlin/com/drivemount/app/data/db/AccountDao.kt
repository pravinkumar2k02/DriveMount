package com.drivemount.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for [AccountEntity] operations.
 *
 * Provides both reactive [Flow]-based observation of all accounts and
 * synchronous accessors for use in interceptors and background workers.
 */
@Dao
interface AccountDao {

    /**
     * Observes all signed-in accounts.
     *
     * @return A [Flow] emitting the complete list of accounts whenever it changes.
     */
    @Query("SELECT * FROM accounts ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    /**
     * Retrieves all signed-in accounts synchronously (blocking).
     *
     * Suitable for interceptors and other non-coroutine contexts.
     *
     * @return The list of all accounts.
     */
    @Query("SELECT * FROM accounts ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllAccountsSync(): List<AccountEntity>

    /**
     * Retrieves a single account by its ID (email address).
     *
     * @param accountId The account email.
     * @return The [AccountEntity] or null if not found.
     */
    @Query("SELECT * FROM accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): AccountEntity?

    /**
     * Retrieves a single account by its ID synchronously (blocking).
     *
     * Suitable for OkHttp interceptors.
     *
     * @param accountId The account email.
     * @return The [AccountEntity] or null if not found.
     */
    @Query("SELECT * FROM accounts WHERE accountId = :accountId LIMIT 1")
    fun getAccountSync(accountId: String): AccountEntity?

    /**
     * Inserts or replaces an account entity.
     *
     * @param account The account to upsert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    /**
     * Updates the OAuth tokens and expiry for an account.
     *
     * @param accountId The account email.
     * @param accessToken The new access token.
     * @param tokenExpiry Epoch millis when the access token expires.
     */
    @Query(
        """
        UPDATE accounts 
        SET accessToken = :accessToken, 
            tokenExpiry = :tokenExpiry 
        WHERE accountId = :accountId
        """
    )
    suspend fun updateTokens(accountId: String, accessToken: String, tokenExpiry: Long)

    /**
     * Updates the OAuth tokens and expiry for an account synchronously (blocking).
     *
     * Suitable for OkHttp interceptors.
     *
     * @param accountId The account email.
     * @param accessToken The new access token.
     * @param tokenExpiry Epoch millis when the access token expires.
     */
    @Query(
        """
        UPDATE accounts 
        SET accessToken = :accessToken, 
            tokenExpiry = :tokenExpiry 
        WHERE accountId = :accountId
        """
    )
    fun updateTokensSync(accountId: String, accessToken: String, tokenExpiry: Long)

    /**
     * Updates the storage quota information for an account.
     *
     * @param accountId The account email.
     * @param storageLimit Total storage limit in bytes.
     * @param storageUsed Total storage used in bytes.
     */
    @Query(
        """
        UPDATE accounts 
        SET storageLimit = :storageLimit, 
            storageUsed = :storageUsed 
        WHERE accountId = :accountId
        """
    )
    suspend fun updateStorageQuota(accountId: String, storageLimit: Long, storageUsed: Long)

    /**
     * Updates the changes page token used for incremental sync.
     *
     * @param accountId The account email.
     * @param token The new changes page token.
     */
    @Query("UPDATE accounts SET changesPageToken = :token WHERE accountId = :accountId")
    suspend fun updateChangesPageToken(accountId: String, token: String)

    /**
     * Deletes an account by its ID.
     *
     * @param accountId The account email to remove.
     */
    @Query("DELETE FROM accounts WHERE accountId = :accountId")
    suspend fun delete(accountId: String)
}
