package com.drivemount.app.provider

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemount.app.util.Constants
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying the registration and SAF URI resolution of [DriveDocumentsProvider].
 */
@RunWith(AndroidJUnit4::class)
class DriveDocumentsProviderTest {

    private lateinit var contentResolver: ContentResolver

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver
    }

    @Test
    fun testProviderAuthorityRegistration() {
        val rootsUri: Uri = DocumentsContract.buildRootsUri(Constants.AUTHORITY)
        assertNotNull(rootsUri)
        assertTrue(rootsUri.authority == Constants.AUTHORITY)

        // Query roots table via ContentResolver
        val cursor = contentResolver.query(rootsUri, null, null, null, null)
        assertNotNull("Cursor returned from queryRoots should not be null", cursor)
        cursor?.use {
            // Verify expected SAF roots columns exist in cursor
            assertTrue(it.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID) != -1)
            assertTrue(it.getColumnIndex(DocumentsContract.Root.COLUMN_FLAGS) != -1)
            assertTrue(it.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE) != -1)
            assertTrue(it.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID) != -1)
        }
    }

    @Test
    fun testBuildDocumentUriFormat() {
        val documentId = "testuser@gmail.com:sample_file_id"
        val docUri = DocumentsContract.buildDocumentUri(Constants.AUTHORITY, documentId)

        assertNotNull(docUri)
        assertTrue(docUri.authority == Constants.AUTHORITY)
        assertTrue(DocumentsContract.getDocumentId(docUri) == documentId)
    }

    @Test
    fun testBuildChildDocumentsUriFormat() {
        val parentDocId = "testuser@gmail.com:root"
        val childUri = DocumentsContract.buildChildDocumentsUri(Constants.AUTHORITY, parentDocId)

        assertNotNull(childUri)
        assertTrue(childUri.authority == Constants.AUTHORITY)
        assertTrue(childUri.toString().contains("children"))
    }
}

