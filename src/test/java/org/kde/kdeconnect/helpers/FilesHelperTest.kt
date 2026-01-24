/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import org.junit.Assert
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.pathString

class FilesHelperTest {

    @Test
    fun findValidNonExistingFileNameForFile() {
        val temporaryTestFolder = Files.createTempDirectory("kde_connect_file_unit_test")

        val baseName = "example_file"
        val extension = "txt"
        val fileName = "$baseName.$extension"

        val firstFile = temporaryTestFolder.resolve(fileName).toFile()
        val secondFile = temporaryTestFolder.resolve("$baseName (1).$extension").toFile()
        val thirdFile = temporaryTestFolder.resolve("$baseName (2).$extension").toFile()

        firstFile.delete()
        secondFile.delete()
        thirdFile.delete()

        firstFile.deleteOnExit()
        secondFile.deleteOnExit()
        thirdFile.deleteOnExit()

        val result1 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, fileName)
        Assert.assertEquals(fileName, result1)

        firstFile.createNewFile()
        val result2 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, fileName)
        Assert.assertEquals("$baseName (1).$extension", result2)

        secondFile.createNewFile()
        val result3 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, fileName)
        Assert.assertEquals("$baseName (2).$extension", result3)

        thirdFile.createNewFile()
        val result4 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, fileName)
        Assert.assertEquals("$baseName (3).$extension", result4)

        val invalidCharsFileName1 = "test%cfile%cname%c.txt".format(0x00, 0x1F, 0x7F)
        val result5 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, invalidCharsFileName1)
        Assert.assertTrue(result5.startsWith("test_file_name_"))
        Assert.assertTrue(result5.endsWith(".txt"))

        val invalidCharsFileName2 = "test:file|name.txt"
        val result6 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, invalidCharsFileName2)
        Assert.assertTrue(result6.startsWith("test_file_name"))
        Assert.assertTrue(result6.endsWith(".txt"))

        val longFileName = (0..300).joinToString("") { "A" } + ".txt"
        val result7 = FilesHelper.findValidNonExistingFileNameForFile(temporaryTestFolder.pathString, longFileName)
        Assert.assertTrue(result7.length <= 255)
    }
    // Other functions require Android classes
}
