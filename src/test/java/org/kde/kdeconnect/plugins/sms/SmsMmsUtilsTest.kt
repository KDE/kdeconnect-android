/*
 * SPDX-FileCopyrightText: 2026 Masum Ali <heymasum@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sms

import android.content.Context
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.plugins.sms.SmsMmsUtils

class SmsMmsUtilsTest {
    private val context: Context = mockk()
    private val telephonyManager: TelephonyManager = mockk()

    @Before
    fun setUp() {
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager
        every { telephonyManager.networkCountryIso } returns "US"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun stripDuplicatePhoneNumbersRemovesDuplicatesAcrossFormats() {
        val phoneNumbers = "+1-202-555-0173, 2025550173, +12025550173, +1-202-555-0199"
        val result = SmsMmsUtils.stripDuplicatePhoneNumbers(context, phoneNumbers)

        Assert.assertEquals(2, result.size)
        Assert.assertEquals("+1-202-555-0173", result[0].address)
        Assert.assertEquals("+1-202-555-0199", result[1].address)
    }
}
