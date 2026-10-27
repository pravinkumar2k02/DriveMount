package com.drivemount.app.upload

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Minimal foreground service declaration required for Android 14+ (API 34+)
 * dataSync foreground service type compatibility.
 */
class UploadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}

