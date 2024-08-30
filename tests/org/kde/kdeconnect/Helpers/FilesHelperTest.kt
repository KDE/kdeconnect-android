/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.pathString

class FilesHelperTest {
    @Test
    fun findNonExistentName() {
        val temporaryTestFolder = Files.createTempDirectory("kde_connect_file_unit_test")

        val baseName = "example_file"
        val firstFile = temporaryTestFolder.resolve("$baseName").toFile()
        val secondFile = temporaryTestFolder.resolve("$baseName (1).").toFile()

        firstFile.delete()
        secondFile.delete()

        // in case the test fails
        firstFile.deleteOnExit()
        secondFile.deleteOnExit()

        val firstFileName = FilesHelper.findNonExistingNameForNewFile(temporaryTestFolder.pathString, baseName)
        firstFile.createNewFile()
        Assert.assertEquals("$baseName", firstFileName)

        val secondFileName = FilesHelper.findNonExistingNameForNewFile(temporaryTestFolder.pathString, baseName)
        Assert.assertEquals("$baseName (1).", secondFileName)
        secondFile.createNewFile()

        val thirdFileName = FilesHelper.findNonExistingNameForNewFile(temporaryTestFolder.pathString, baseName)
        Assert.assertEquals("$baseName (2).", thirdFileName)
    }

    @Test
    fun fileSystemSafeName() {
        val notTooLong = (0..254).joinToString("") { "A" }
        val tooLong = (0..255).joinToString("") { "A" }
        Assert.assertEquals(notTooLong, FilesHelper.toFileSystemSafeName(notTooLong))
        Assert.assertEquals(notTooLong, FilesHelper.toFileSystemSafeName(tooLong))

        Assert.assertEquals("Averyspecialfile", FilesHelper.toFileSystemSafeName("A very special file \uD83E\uDD70"))
        Assert.assertEquals("A_very_special_file", FilesHelper.toFileSystemSafeName("A_very_special_file \uD83E\uDD70"))
        Assert.assertEquals("12345", FilesHelper.toFileSystemSafeName("1 2 3 4 5"))
        Assert.assertEquals("1-2.3/4\\5", FilesHelper.toFileSystemSafeName("1-2.3/4\\5"))
        Assert.assertEquals("___", FilesHelper.toFileSystemSafeName("  _ _ _  "))
    }

    // Other functions require Android classes
}