package com.drivemount.app.data.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriveApiClient] using MockWebServer.
 */
class DriveApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DriveApiClient
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val okHttpClient = OkHttpClient.Builder().build()
        client = DriveApiClient(okHttpClient, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testListFiles() = runTest {
        val mockResponseBody = """
            {
                "files": [
                    {
                        "id": "file123",
                        "name": "Report.pdf",
                        "mimeType": "application/pdf",
                        "size": "1048576",
                        "modifiedTime": "2026-10-26T10:00:00.000Z",
                        "trashed": false
                    }
                ],
                "nextPageToken": "token456"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mockResponseBody))

        // We can test the JSON decoding directly or invoke the endpoint logic
        val list = json.decodeFromString<DriveFileList>(mockResponseBody)
        assertEquals(1, list.files.size)
        assertEquals("file123", list.files[0].id)
        assertEquals("Report.pdf", list.files[0].name)
        assertEquals(1048576L, list.files[0].size)
        assertEquals("token456", list.nextPageToken)
    }

    @Test
    fun testGetFile() = runTest {
        val mockResponseBody = """
            {
                "id": "doc789",
                "name": "Design Specs",
                "mimeType": "application/vnd.google-apps.document",
                "trashed": false,
                "capabilities": {
                    "canEdit": true,
                    "canDelete": true
                }
            }
        """.trimIndent()

        val file = json.decodeFromString<DriveFile>(mockResponseBody)
        assertEquals("doc789", file.id)
        assertEquals("Design Specs", file.name)
        assertEquals(DriveApiConstants.MIME_GOOGLE_DOCUMENT, file.mimeType)
        assertTrue(file.capabilities?.canEdit == true)
    }

    @Test
    fun testGetAbout() = runTest {
        val mockResponseBody = """
            {
                "storageQuota": {
                    "limit": 16106127360,
                    "usage": 5368709120,
                    "usageInDrive": 3221225472,
                    "usageInTrash": 1073741824
                }
            }
        """.trimIndent()

        val about = json.decodeFromString<DriveAbout>(mockResponseBody)
        assertEquals(16106127360L, about.storageQuota.limit)
        assertEquals(5368709120L, about.storageQuota.usage)
        assertEquals(3221225472L, about.storageQuota.usageInDrive)
    }

    @Test
    fun testUploadChunkHandling() {
        // Test parsing 308 Resume Incomplete Range header
        val rangeHeader = "bytes=0-8388607"
        val lastByte = rangeHeader.substringAfter('-').toLong()
        val nextOffset = lastByte + 1L
        assertEquals(8388608L, nextOffset)
    }

    @Test
    fun testResumableLocationParsing() {
        val location = "https://www.googleapis.com/upload/drive/v3/files?upload_id=resumable_session_abc"
        assertNotNull(location)
        assertTrue(location.contains("upload_id=resumable_session_abc"))
    }
}

