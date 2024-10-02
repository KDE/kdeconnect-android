package org.kde.kdeconnect.Helpers

import android.net.Uri
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.URI
import java.util.ArrayList

class StorageHelperTest {
    @Test
    fun testSamples() {
        val uriAndDocumentIds: Map<Uri, String> = mapOf(
            "content://com.android.providers.downloads.documents/tree/downloads" to "downloads",
            "content://com.android.externalstorage.documents/tree/1715-1D1F:" to "1715-1D1F",
            "content://com.android.externalstorage.documents/tree/1715-1D1F:My%20Photos" to "My Photos",
            "content://com.android.externalstorage.documents/tree/primary:" to "primary",
            "content://com.android.externalstorage.documents/tree/primary:DCIM" to "DCIM",
            "content://com.android.externalstorage.documents/tree/primary:Download/bla" to "Download/bla",
        ).mapKeys { entry ->
            val mockUri = mock(Uri::class.java)
            // e.g. "content://com.android.providers.downloads.documents/tree/downloads" -> ["tree", "downloads"]
            val pathSegments = URI.create(entry.key).path.split("/").drop(1)
            val pathSegmentsJavaList: java.util.ArrayList<String> = ArrayList(pathSegments)
            `when`(mockUri.pathSegments).thenReturn(pathSegmentsJavaList)
            return@mapKeys mockUri
        }

        for ((treeUri: Uri, knownDocumentId: String) in uriAndDocumentIds) {
            val extractedDocumentId = StorageHelper.getDisplayName(treeUri)
            Assert.assertEquals(knownDocumentId, extractedDocumentId)
        }
    }

    @Test
    fun testMissingTree() {
        val mockUri = mock(Uri::class.java)
        val pathSegmentsJavaList: java.util.ArrayList<String> = ArrayList(listOf("branch", "downloads"))
        `when`(mockUri.pathSegments).thenReturn(pathSegmentsJavaList)
        Assert.assertThrows(IllegalArgumentException::class.java) {
            StorageHelper.getDisplayName(mockUri)
        }
    }
}