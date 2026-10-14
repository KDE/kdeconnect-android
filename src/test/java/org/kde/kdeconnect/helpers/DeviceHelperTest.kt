/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import android.text.SpannableString
import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceHelperTest {

    @Test
    fun testDeviceNameInputFilter() {
        // When valid characters (letters, numbers, spaces, allowed symbols) are passed, filter returns null to preserve IME composing state
        Assert.assertNull(DeviceHelper.filterDeviceNameInput("My Phone", 0, "My Phone".length))
        Assert.assertNull(DeviceHelper.filterDeviceNameInput("a", 0, 1))
        Assert.assertNull(DeviceHelper.filterDeviceNameInput("Pixel 7 Pro", 0, "Pixel 7 Pro".length))
        Assert.assertNull(DeviceHelper.filterDeviceNameInput("Phone-123_test", 0, "Phone-123_test".length))

        // When invalid characters are present, they are stripped
        val filteredWithPunctuation = DeviceHelper.filterDeviceNameInput("Device: 1!", 0, "Device: 1!".length)
        Assert.assertEquals("Device 1", filteredWithPunctuation.toString())

        val filteredWithBracketsAndQuotes = DeviceHelper.filterDeviceNameInput("My [Phone] 'Test' <2>", 0, "My [Phone] 'Test' <2>".length)
        Assert.assertEquals("My Phone Test 2", filteredWithBracketsAndQuotes.toString())

        // Subsequence ranges
        val fullString = "Prefix: 'Valid' Suffix"
        // Target "'Valid'" inside range [8, 15)
        val filteredSub = DeviceHelper.filterDeviceNameInput(fullString, 8, 15)
        Assert.assertEquals("Valid", filteredSub.toString())
    }
}
