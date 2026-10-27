package com.drivemount.app.util

/**
 * Application-wide constants used throughout the DriveMount app.
 *
 * Contains authority strings, cache configuration, retry policies,
 * sync intervals, and notification identifiers.
 */
object Constants {

    /** Content provider authority for the SAF DocumentsProvider. */
    const val AUTHORITY = "com.drivemount.app.documents"

    /**
     * Duration in milliseconds after which cached directory listings
     * are considered stale and should be refreshed from the API.
     * Default: 5 minutes.
     */
    const val CACHE_STALE_THRESHOLD_MS = 5L * 60L * 1000L

    /**
     * Size of a single read block in bytes used by [com.drivemount.app.data.cache.BlockCache].
     * Default: 1 MB.
     */
    const val BLOCK_SIZE = 1024L * 1024L

    /**
     * Default maximum disk cache size in megabytes for the block cache.
     * Can be overridden via [com.drivemount.app.settings.SettingsStore].
     */
    const val DEFAULT_CACHE_SIZE_MB = 512

    /**
     * Maximum number of retry attempts for failed uploads before
     * marking them as permanently failed.
     */
    const val MAX_RETRY_COUNT = 5

    /**
     * Initial backoff duration in milliseconds for exponential retry.
     * Subsequent retries double this value.
     */
    const val INITIAL_BACKOFF_MS = 1000L

    /**
     * Interval in minutes between periodic background sync operations
     * that poll Google Drive for changes.
     */
    const val SYNC_INTERVAL_MINUTES = 15L

    /** Notification channel ID used for upload progress notifications. */
    const val NOTIFICATION_CHANNEL_ID = "upload_channel"

    /** Notification ID for the foreground upload notification. */
    const val NOTIFICATION_ID = 1001
}
