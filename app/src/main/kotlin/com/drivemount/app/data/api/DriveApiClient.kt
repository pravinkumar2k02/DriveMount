package com.drivemount.app.data.api

import com.drivemount.app.data.api.DriveApiConstants.BASE_URL
import com.drivemount.app.data.api.DriveApiConstants.FIELDS_FILE
import com.drivemount.app.data.api.DriveApiConstants.FIELDS_LIST
import com.drivemount.app.data.api.DriveApiConstants.HEADER_ACCOUNT_ID
import com.drivemount.app.data.api.DriveApiConstants.PAGE_SIZE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Low-level HTTP client for the Google Drive REST API v3.
 *
 * All methods execute OkHttp calls synchronously inside [Dispatchers.IO]
 * coroutine contexts. The injected [OkHttpClient] is expected to have an
 * [com.drivemount.app.auth.AuthInterceptor] that reads the custom
 * [DriveApiConstants.HEADER_ACCOUNT_ID] header and replaces it with the
 * appropriate `Authorization: Bearer` header.
 *
 * @property httpClient OkHttp client pre-configured with auth interceptors.
 * @property json kotlinx.serialization [Json] instance for parsing responses.
 */
@Singleton
class DriveApiClient @Inject constructor(
    @Named("drive") private val httpClient: OkHttpClient,
    private val json: Json,
) {

    // ── File Listing ─────────────────────────────────────────────────────

    /**
     * Lists files within a parent folder.
     *
     * @param accountId The account email for authorization.
     * @param parentId The ID of the parent folder (use "root" for My Drive).
     * @param pageToken Optional token for fetching subsequent pages.
     * @param query Optional search term; filters by `name contains`.
     * @return A [DriveFileList] with matching files and pagination info.
     * @throws IOException on network or HTTP errors.
     */
    suspend fun listFiles(
        accountId: String,
        parentId: String,
        pageToken: String? = null,
        query: String? = null,
    ): DriveFileList = withContext(Dispatchers.IO) {
        val q = buildString {
            append("'")
            append(parentId)
            append("' in parents and trashed=false")
            if (!query.isNullOrBlank()) {
                append(" and name contains '")
                append(query.replace("'", "\\'"))
                append("'")
            }
        }
        val urlBuilder = StringBuilder(BASE_URL)
            .append("files?")
            .append("q=").append(encode(q))
            .append("&fields=").append(encode(FIELDS_LIST))
            .append("&pageSize=").append(PAGE_SIZE)
            .append("&orderBy=").append(encode("folder,name"))
        if (pageToken != null) {
            urlBuilder.append("&pageToken=").append(encode(pageToken))
        }

        val request = newGetRequest(accountId, urlBuilder.toString())
        val body = executeForBody(request)
        json.decodeFromString<DriveFileList>(body)
    }

    /**
     * Retrieves metadata for a single file.
     *
     * @param accountId The account email for authorization.
     * @param fileId The opaque ID of the file.
     * @return The [DriveFile] metadata.
     */
    suspend fun getFile(
        accountId: String,
        fileId: String,
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId?fields=${encode(FIELDS_FILE)}"
        val request = newGetRequest(accountId, url)
        val body = executeForBody(request)
        json.decodeFromString<DriveFile>(body)
    }

    // ── Download ─────────────────────────────────────────────────────────

    /**
     * Downloads a byte range of a binary file.
     *
     * @param accountId The account email for authorization.
     * @param fileId The file ID.
     * @param start Start byte offset (inclusive).
     * @param end End byte offset (inclusive).
     * @return The requested byte range.
     */
    suspend fun downloadRange(
        accountId: String,
        fileId: String,
        start: Long,
        end: Long,
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_ACCOUNT_ID, accountId)
            .addHeader("Range", "bytes=$start-$end")
            .get()
            .build()
        executeForBytes(request)
    }

    /**
     * Exports a Google Workspace file in the specified format.
     *
     * @param accountId The account email for authorization.
     * @param fileId The file ID.
     * @param exportMimeType The target MIME type (e.g., application/pdf).
     * @return The exported file bytes.
     */
    suspend fun exportFile(
        accountId: String,
        fileId: String,
        exportMimeType: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId/export?mimeType=${encode(exportMimeType)}"
        val request = newGetRequest(accountId, url)
        executeForBytes(request)
    }

    // ── About ────────────────────────────────────────────────────────────

    /**
     * Retrieves storage quota information for the authenticated account.
     *
     * @param accountId The account email for authorization.
     * @return A [DriveAbout] containing the storage quota.
     */
    suspend fun getAbout(accountId: String): DriveAbout = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}about?fields=${encode("storageQuota")}"
        val request = newGetRequest(accountId, url)
        val body = executeForBody(request)
        json.decodeFromString<DriveAbout>(body)
    }

    // ── File Operations ──────────────────────────────────────────────────

    /**
     * Creates a new empty file with the given metadata.
     *
     * @param accountId The account email for authorization.
     * @param name Display name of the new file.
     * @param parentId ID of the parent folder.
     * @param mimeType MIME type of the file.
     * @return The created [DriveFile].
     */
    suspend fun createFile(
        accountId: String,
        name: String,
        parentId: String,
        mimeType: String,
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files?fields=${encode(FIELDS_FILE)}"
        val metadata = buildJsonObject(
            "name" to "\"$name\"",
            "mimeType" to "\"$mimeType\"",
            "parents" to "[\"$parentId\"]",
        )
        val request = newPostRequest(accountId, url, metadata)
        val body = executeForBody(request)
        json.decodeFromString<DriveFile>(body)
    }

    /**
     * Creates a new folder with the given name.
     *
     * @param accountId The account email for authorization.
     * @param name Display name of the new folder.
     * @param parentId ID of the parent folder.
     * @return The created [DriveFile] representing the folder.
     */
    suspend fun createFolder(
        accountId: String,
        name: String,
        parentId: String,
    ): DriveFile = createFile(
        accountId = accountId,
        name = name,
        parentId = parentId,
        mimeType = DriveApiConstants.MIME_GOOGLE_FOLDER,
    )

    /**
     * Soft-deletes a file by setting its `trashed` property to `true`.
     *
     * @param accountId The account email for authorization.
     * @param fileId The ID of the file to trash.
     */
    suspend fun deleteFile(
        accountId: String,
        fileId: String,
    ): Unit = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId"
        val body = """{"trashed":true}"""
        val request = newPatchRequest(accountId, url, body)
        executeForBody(request)
    }

    /**
     * Renames a file.
     *
     * @param accountId The account email for authorization.
     * @param fileId The ID of the file.
     * @param newName The new display name.
     * @return The updated [DriveFile].
     */
    suspend fun renameFile(
        accountId: String,
        fileId: String,
        newName: String,
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId?fields=${encode(FIELDS_FILE)}"
        val body = """{"name":"$newName"}"""
        val request = newPatchRequest(accountId, url, body)
        val responseBody = executeForBody(request)
        json.decodeFromString<DriveFile>(responseBody)
    }

    /**
     * Moves a file from one parent folder to another.
     *
     * @param accountId The account email for authorization.
     * @param fileId The ID of the file.
     * @param newParentId The destination parent folder ID.
     * @param oldParentId The source parent folder ID.
     * @return The updated [DriveFile].
     */
    suspend fun moveFile(
        accountId: String,
        fileId: String,
        newParentId: String,
        oldParentId: String,
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId" +
            "?addParents=${encode(newParentId)}" +
            "&removeParents=${encode(oldParentId)}" +
            "&fields=${encode(FIELDS_FILE)}"
        val body = """{}"""
        val request = newPatchRequest(accountId, url, body)
        val responseBody = executeForBody(request)
        json.decodeFromString<DriveFile>(responseBody)
    }

    /**
     * Copies a file to a new parent folder with an optional new name.
     *
     * @param accountId The account email for authorization.
     * @param fileId The ID of the file to copy.
     * @param newParentId The destination parent folder ID.
     * @param newName The name for the copy.
     * @return The newly created [DriveFile] copy.
     */
    suspend fun copyFile(
        accountId: String,
        fileId: String,
        newParentId: String,
        newName: String,
    ): DriveFile = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}files/$fileId/copy?fields=${encode(FIELDS_FILE)}"
        val metadata = buildJsonObject(
            "name" to "\"$newName\"",
            "parents" to "[\"$newParentId\"]",
        )
        val request = newPostRequest(accountId, url, metadata)
        val responseBody = executeForBody(request)
        json.decodeFromString<DriveFile>(responseBody)
    }

    // ── Resumable Upload ─────────────────────────────────────────────────

    /**
     * Initiates a resumable upload session and returns the upload URI.
     *
     * @param accountId The account email for authorization.
     * @param fileId Optional file ID (for updating an existing file; null for new files).
     * @param name Display name of the file being uploaded.
     * @param parentId The parent folder ID.
     * @param mimeType The MIME type of the file content.
     * @param fileSize Total size of the file in bytes.
     * @return The resumable upload URI from the `Location` response header.
     * @throws IOException if the response does not include a Location header.
     */
    suspend fun initiateResumableUpload(
        accountId: String,
        fileId: String?,
        name: String,
        parentId: String,
        mimeType: String,
        fileSize: Long,
    ): String = withContext(Dispatchers.IO) {
        val url = if (fileId != null) {
            "${DriveApiConstants.UPLOAD_URL}/$fileId?uploadType=resumable"
        } else {
            "${DriveApiConstants.UPLOAD_URL}?uploadType=resumable"
        }

        val metadata = if (fileId != null) {
            buildJsonObject("name" to "\"$name\"")
        } else {
            buildJsonObject(
                "name" to "\"$name\"",
                "parents" to "[\"$parentId\"]",
            )
        }

        val method = if (fileId != null) "PATCH" else "POST"
        val requestBody = metadata.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader(HEADER_ACCOUNT_ID, accountId)
            .addHeader("X-Upload-Content-Type", mimeType)
            .addHeader("X-Upload-Content-Length", fileSize.toString())
            .method(method, requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Initiate resumable upload failed: ${response.code} ${response.message}"
                )
            }
            response.header("Location")
                ?: throw IOException("No Location header in resumable upload initiation response")
        }
    }

    /**
     * Uploads a chunk of data to a resumable upload session.
     *
     * @param uploadUri The resumable upload URI.
     * @param chunk The bytes to upload in this chunk.
     * @param offset The byte offset of this chunk in the total file.
     * @param totalSize Total file size in bytes.
     * @return `true` if the upload is complete (HTTP 200/201), `false` if
     *   more chunks are needed (HTTP 308 Resume Incomplete).
     * @throws IOException on unexpected HTTP status codes.
     */
    suspend fun uploadChunk(
        uploadUri: String,
        chunk: ByteArray,
        offset: Long,
        totalSize: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val endByte = offset + chunk.size - 1
        val contentRange = "bytes $offset-$endByte/$totalSize"
        val requestBody = chunk.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url(uploadUri)
            .addHeader("Content-Range", contentRange)
            .put(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                200, 201 -> {
                    Timber.d("Upload complete for URI: $uploadUri")
                    true
                }
                308 -> {
                    Timber.d("Chunk uploaded, resuming. Range header: ${response.header("Range")}")
                    false
                }
                else -> throw IOException(
                    "Upload chunk failed: ${response.code} ${response.message}"
                )
            }
        }
    }

    /**
     * Queries the current upload progress for a resumable session.
     *
     * Sends a request with `Content-Range: bytes *​/totalSize` to check
     * how many bytes the server has received so far.
     *
     * @param uploadUri The resumable upload URI.
     * @return The byte offset from which to resume uploading.
     * @throws IOException on unexpected HTTP status codes.
     */
    suspend fun getUploadProgress(uploadUri: String): Long = withContext(Dispatchers.IO) {
        val requestBody = ByteArray(0).toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(uploadUri)
            .addHeader("Content-Range", "bytes */*")
            .put(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                200, 201 -> {
                    Timber.d("Upload already complete for URI: $uploadUri")
                    -1L // signals complete
                }
                308 -> {
                    val rangeHeader = response.header("Range")
                    if (rangeHeader != null) {
                        // Range: bytes=0-12345
                        val endOffset = rangeHeader.substringAfter("-").toLongOrNull() ?: 0L
                        endOffset + 1L
                    } else {
                        0L // server has received nothing yet
                    }
                }
                else -> throw IOException(
                    "Get upload progress failed: ${response.code} ${response.message}"
                )
            }
        }
    }

    // ── Changes ──────────────────────────────────────────────────────────

    /**
     * Retrieves the start page token for watching future changes.
     *
     * @param accountId The account email for authorization.
     * @return The start page token string.
     */
    suspend fun getChangesStartPageToken(
        accountId: String,
    ): String = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}changes/startPageToken"
        val request = newGetRequest(accountId, url)
        val body = executeForBody(request)
        json.decodeFromString<DriveStartPageToken>(body).startPageToken
    }

    /**
     * Lists changes since the given page token.
     *
     * @param accountId The account email for authorization.
     * @param pageToken The page token from a previous changes response or start page token.
     * @return A [DriveChangeList] with changes and the next page token.
     */
    suspend fun getChanges(
        accountId: String,
        pageToken: String,
    ): DriveChangeList = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}changes?" +
            "pageToken=${encode(pageToken)}" +
            "&fields=${encode("changes(fileId,file($FIELDS_FILE),removed,changeType,time),nextPageToken,newStartPageToken")}" +
            "&pageSize=$PAGE_SIZE"
        val request = newGetRequest(accountId, url)
        val body = executeForBody(request)
        json.decodeFromString<DriveChangeList>(body)
    }

    // ── Search ───────────────────────────────────────────────────────────

    /**
     * Searches for files across the entire Drive matching a query string.
     *
     * @param accountId The account email for authorization.
     * @param query The search term; filters by `name contains` and `fullText contains`.
     * @param pageToken Optional pagination token.
     * @return A [DriveFileList] with matching files.
     */
    suspend fun searchFiles(
        accountId: String,
        query: String,
        pageToken: String? = null,
    ): DriveFileList = withContext(Dispatchers.IO) {
        val q = "name contains '${query.replace("'", "\\'")}' and trashed=false"
        val urlBuilder = StringBuilder(BASE_URL)
            .append("files?")
            .append("q=").append(encode(q))
            .append("&fields=").append(encode(FIELDS_LIST))
            .append("&pageSize=").append(PAGE_SIZE)
            .append("&orderBy=").append(encode("folder,name"))
        if (pageToken != null) {
            urlBuilder.append("&pageToken=").append(encode(pageToken))
        }
        val request = newGetRequest(accountId, urlBuilder.toString())
        val body = executeForBody(request)
        json.decodeFromString<DriveFileList>(body)
    }

    // ── Thumbnail ────────────────────────────────────────────────────────

    /**
     * Downloads a thumbnail image.
     *
     * @param accountId The account email for authorization.
     * @param thumbnailUrl The thumbnail URL from a [DriveFile.thumbnailLink].
     * @return The raw image bytes.
     */
    suspend fun getThumbnail(
        accountId: String,
        thumbnailUrl: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = newGetRequest(accountId, thumbnailUrl)
        executeForBytes(request)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private fun newGetRequest(accountId: String, url: String): Request =
        Request.Builder()
            .url(url)
            .addHeader(HEADER_ACCOUNT_ID, accountId)
            .get()
            .build()

    private fun newPostRequest(accountId: String, url: String, jsonBody: String): Request =
        Request.Builder()
            .url(url)
            .addHeader(HEADER_ACCOUNT_ID, accountId)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun newPatchRequest(accountId: String, url: String, jsonBody: String): Request =
        Request.Builder()
            .url(url)
            .addHeader(HEADER_ACCOUNT_ID, accountId)
            .patch(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    /**
     * Executes a request and returns the response body as a string.
     *
     * @throws IOException on network errors or non-2xx responses.
     */
    private fun executeForBody(request: Request): String {
        Timber.d("HTTP ${request.method} ${request.url}")
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(500) ?: ""
                Timber.e("HTTP ${response.code}: $errorBody")
                throw IOException(
                    "HTTP ${response.code} ${response.message}: $errorBody"
                )
            }
            response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    /**
     * Executes a request and returns the response body as raw bytes.
     *
     * @throws IOException on network errors or non-2xx responses.
     */
    private fun executeForBytes(request: Request): ByteArray {
        Timber.d("HTTP ${request.method} ${request.url}")
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(500) ?: ""
                Timber.e("HTTP ${response.code}: $errorBody")
                throw IOException(
                    "HTTP ${response.code} ${response.message}: $errorBody"
                )
            }
            response.body?.bytes() ?: throw IOException("Empty response body")
        }
    }

    /**
     * Builds a minimal JSON object string from key-value pairs where values
     * are already formatted as JSON tokens (e.g., `"\"hello\""` or `"[1,2]"`).
     */
    private fun buildJsonObject(vararg pairs: Pair<String, String>): String = buildString {
        append('{')
        pairs.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(key).append("\":").append(value)
        }
        append('}')
    }

    /** URL-encodes a string using UTF-8. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
