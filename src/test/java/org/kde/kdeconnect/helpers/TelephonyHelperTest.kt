/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import org.junit.Assert
import org.junit.Test

class TelephonyHelperTest {
    @Test
    fun canonicalizePhoneNumber() {
        val phoneNumbers = mapOf(
            "12025550173" to "+1-202-555-0173",     // US number
            "447911123456" to "+44 7911 123456",    // UK mobile number
            "33142685300" to "+33 1 42 68 53 00",   // France number with spaces
            "919876543210" to "+91-9876543210",     // India number with dashes
            "61400123456" to "(+61) 400 123 456",   // Australia number with country code in parentheses
            "81312345678" to "+81-3-1234-5678",     // Japan number with dashes
            "5511912345678" to "+55 (11) 91234-5678", // Brazil mobile with parentheses
            "4930123456" to "+49 30 123456",        // Germany number with spaces
            "861012345678" to "+86 10 1234 5678",   // China number with spaces
            "34600123456" to "+34 600 123 456",     // Spain mobile number
            "74951234567" to "+7 495 123-45-67",    // Russia number with dashes
            "6512345678" to "+65 1234 5678",        // Singapore number
        )

        for ((expected, input) in phoneNumbers) {
            val result = TelephonyHelper.canonicalizePhoneNumber(input)
            Assert.assertEquals(expected, result)
        }
    }

    @Test
    fun isValidApnType() {
        val apnTypes = mapOf(
            // Valid combinations
            Pair("mms,supl", "mms") to true,
            Pair("mms,*", "") to true,
            Pair("", "hipri") to true,
            Pair("mms,hipri", "mms") to true,
            Pair("default,mms,dun", "dun") to true,
            // Invalid combinations
            Pair("mms,supl", "default") to false,
            Pair("hipri,supl", "*") to false,
            Pair("supl", "mms") to false,
            Pair("mms,supl", "default") to false,
        )

        for ((input, expected) in apnTypes) {
            val result = TelephonyHelper.isValidApnType(input.first, input.second)
            val message = "Expected $expected for $input, but got $result"
            Assert.assertEquals(message, expected, result)
        }
    }
}
