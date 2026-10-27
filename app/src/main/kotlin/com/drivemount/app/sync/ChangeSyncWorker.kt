package com.drivemount.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drivemount.app.data.db.AccountDao
import com.drivemount.app.data.repository.DriveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic background worker that polls the Google Drive Changes API
 * for incremental remote updates across all mounted accounts.
 */
@HiltWorker
class ChangeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DriveRepository,
    private val accountDao: AccountDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val accounts = accountDao.getAllAccountsSync()
        Timber.d("Starting periodic change sync for %d accounts...", accounts.size)

        for (account in accounts) {
            try {
                repository.syncChanges(account.accountId)
                Timber.d("Sync completed for account %s", account.accountId)
            } catch (e: Exception) {
                Timber.w(e, "Sync failed for account %s", account.accountId)
            }
        }

        return Result.success()
    }
}

