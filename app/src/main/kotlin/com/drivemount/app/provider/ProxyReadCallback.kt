package com.drivemount.app.provider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import com.drivemount.app.data.repository.DriveRepository
import com.drivemount.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * [ProxyFileDescriptorCallback] that enables transparent streaming and random-access seeking
 * for read operations over the Storage Access Framework without downloading entire files.
 *
 * Translates file read requests into 1 MB block accesses served from [com.drivemount.app.data.cache.BlockCache]
 * or on-demand HTTP 206 Partial Content Range requests via Google Drive API.
 */
class ProxyReadCallback(
    private val accountId: String,
    private val fileId: String,
    private val fileSize: Long,
    private val repository: DriveRepository,
    private val scope: CoroutineScope,
) : ProxyFileDescriptorCallback() {

    override fun onGetSize(): Long = fileSize

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (offset >= fileSize) {
            return 0 // End of file
        }

        val bytesToRead = (size.toLong().coerceAtMost(fileSize - offset)).toInt()
        if (bytesToRead <= 0) {
            return 0
        }

        var bytesCopied = 0
        var currentOffset = offset
        val blockSize = Constants.BLOCK_SIZE

        try {
            while (bytesCopied < bytesToRead) {
                val blockIndex = currentOffset / blockSize
                val offsetWithinBlock = (currentOffset % blockSize).toInt()

                // Fetch block with a timeout to avoid hanging the binder thread indefinitely
                val blockData = runBlocking {
                    withTimeout(20_000L) {
                        repository.readBlock(accountId, fileId, blockIndex, fileSize)
                    }
                }

                val availableInBlock = (blockData.size - offsetWithinBlock).coerceAtLeast(0)
                if (availableInBlock == 0) {
                    break // No more data in this block
                }

                val toCopy = minOf(bytesToRead - bytesCopied, availableInBlock)
                System.arraycopy(blockData, offsetWithinBlock, data, bytesCopied, toCopy)

                bytesCopied += toCopy
                currentOffset += toCopy
            }
            return bytesCopied
        } catch (e: Exception) {
            Timber.e(e, "Error reading file %s at offset %d (size %d)", fileId, offset, size)
            throw ErrnoException("onRead", OsConstants.EIO)
        }
    }

    override fun onRelease() {
        scope.cancel()
        Timber.d("Released ProxyFileDescriptor for file %s", fileId)
    }
}

