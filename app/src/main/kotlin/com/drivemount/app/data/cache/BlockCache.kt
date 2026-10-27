package com.drivemount.app.data.cache

import android.content.Context
import com.drivemount.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-performance, LRU disk-based block cache for streaming Google Drive file content.
 *
 * Each block is stored as a separate file on disk inside the application's internal cache directory.
 * An access-ordered map tracks blocks in memory to evict the least recently used blocks when
 * the total cache size exceeds [maxSizeBytes].
 */
@Singleton
class BlockCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir = File(context.cacheDir, "drive_blocks").apply {
        if (!exists()) mkdirs()
    }

    private val lock = ReentrantReadWriteLock()
    private var maxSizeBytes: Long = Constants.DEFAULT_CACHE_SIZE_MB * 1024L * 1024L
    private var currentSizeBytes: Long = 0L

    // Key -> File size in bytes. Access order = true (LRU).
    private val lruMap: MutableMap<String, Long> = Collections.synchronizedMap(
        LinkedHashMap<String, Long>(128, 0.75f, true)
    )

    init {
        // Initialize cache state by scanning existing cache directory
        lock.write {
            val files = cacheDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (file.isFile) {
                    val size = file.length()
                    lruMap[file.name] = size
                    currentSizeBytes += size
                }
            }
            trimToSizeLocked()
        }
        Timber.d("BlockCache initialized. Size: %d bytes, Max: %d bytes", currentSizeBytes, maxSizeBytes)
    }

    /**
     * Updates the maximum cache capacity in megabytes.
     */
    fun setMaxCapacityMb(capacityMb: Int) {
        lock.write {
            maxSizeBytes = capacityMb.toLong() * 1024L * 1024L
            trimToSizeLocked()
        }
    }

    /**
     * Retrieves cached bytes for a specific file block, or null if not in cache.
     */
    fun get(accountId: String, fileId: String, blockOffset: Long): ByteArray? {
        val key = computeKey(accountId, fileId, blockOffset)
        val file = File(cacheDir, key)

        lock.read {
            if (!file.exists()) {
                return null
            }
        }

        // Update LRU order
        lruMap[key]

        return try {
            FileInputStream(file).use { it.readBytes() }
        } catch (e: IOException) {
            Timber.w(e, "Failed to read block cache file: %s", key)
            null
        }
    }

    /**
     * Stores a block of bytes in the cache. Evicts LRU entries if capacity is exceeded.
     */
    fun put(accountId: String, fileId: String, blockOffset: Long, data: ByteArray) {
        val key = computeKey(accountId, fileId, blockOffset)
        val file = File(cacheDir, key)

        try {
            FileOutputStream(file).use { it.write(data) }
            val fileSize = data.size.toLong()

            lock.write {
                val oldSize = lruMap.put(key, fileSize) ?: 0L
                currentSizeBytes += (fileSize - oldSize)
                trimToSizeLocked()
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to write block cache file: %s", key)
        }
    }

    /**
     * Evicts all cached blocks for a specific file.
     */
    fun evictFile(accountId: String, fileId: String) {
        val prefix = hashPrefix("${accountId}_${fileId}_")
        lock.write {
            val iterator = lruMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.startsWith(prefix)) {
                    val file = File(cacheDir, entry.key)
                    if (file.delete()) {
                        currentSizeBytes -= entry.value
                    }
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Evicts all cached blocks.
     */
    fun evictAll() {
        lock.write {
            val files = cacheDir.listFiles() ?: emptyArray()
            for (file in files) {
                file.delete()
            }
            lruMap.clear()
            currentSizeBytes = 0L
        }
        Timber.d("BlockCache evicted completely.")
    }

    /** Current size in bytes of the block cache on disk. */
    fun getCurrentSizeBytes(): Long = lock.read { currentSizeBytes }

    /** Maximum allowed size in bytes. */
    fun getMaxSizeBytes(): Long = lock.read { maxSizeBytes }

    private fun trimToSizeLocked() {
        while (currentSizeBytes > maxSizeBytes && lruMap.isNotEmpty()) {
            val oldest = lruMap.entries.iterator().next()
            val file = File(cacheDir, oldest.key)
            if (file.delete()) {
                currentSizeBytes -= oldest.value
            }
            lruMap.remove(oldest.key)
        }
    }

    private fun computeKey(accountId: String, fileId: String, blockOffset: Long): String {
        val raw = "${accountId}_${fileId}_$blockOffset"
        return sha256(raw)
    }

    private fun hashPrefix(raw: String): String {
        return sha256(raw).substring(0, 16)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

