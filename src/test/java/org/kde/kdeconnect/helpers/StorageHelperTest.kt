package org.kde.kdeconnect.helpers

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.net.URI

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
            val mockUri = mockk<Uri>()
            // e.g. "content://com.android.providers.downloads.documents/tree/downloads" -> ["tree", "downloads"]
            val pathSegments = URI.create(entry.key).path.split("/").drop(1)
            val pathSegmentsJavaList: java.util.ArrayList<String> = ArrayList(pathSegments)
            every { mockUri.pathSegments } returns pathSegmentsJavaList
            return@mapKeys mockUri
        }

        for ((treeUri: Uri, knownDocumentId: String) in uriAndDocumentIds) {
            val extractedDocumentId = StorageHelper.getDisplayName(treeUri)
            Assert.assertEquals(knownDocumentId, extractedDocumentId)
        }
    }

    @Test
    fun testMissingTree() {
        val mockUri = mockk<Uri>()
        val pathSegmentsJavaList = ArrayList(listOf("branch", "downloads"))
        every { mockUri.pathSegments } returns pathSegmentsJavaList
        Assert.assertThrows(IllegalArgumentException::class.java) {
            StorageHelper.getDisplayName(mockUri)
        }
    }
}
