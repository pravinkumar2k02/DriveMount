package com.drivemount.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.drivemount.app.sync.ChangeSyncWorker
import com.drivemount.app.upload.UploadManager
import com.drivemount.app.util.Constants
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Custom [Application] class for DriveMount.
 *
 * Configures Timber logging, Hilt WorkManager initialization, notification channels,
 * and periodic background sync with Google Drive.
 */
@HiltAndroidApp
class DriveMountApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var uploadManager: UploadManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 2. Setup notification channels
        uploadManager.createNotificationChannel()

        // 3. Schedule periodic background sync
        schedulePeriodicChangeSync()
    }

    private fun schedulePeriodicChangeSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<ChangeSyncWorker>(
            Constants.SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DriveChangePeriodicSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Timber.i("Periodic Google Drive changes sync scheduled every %d minutes.", Constants.SYNC_INTERVAL_MINUTES)
    }
}

