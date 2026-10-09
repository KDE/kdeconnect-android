/*
 * SPDX-FileCopyrightText: 2019 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

object TelephonyHelper {

    const val LOGGING_TAG: String = "TelephonyHelper"

    /**
     * Try to get the phone number currently active on the phone
     *
     * Make sure that you have the READ_PHONE_STATE permission!
     *
     * Note that entries of the returned list might return null if the phone number is not known by the device
     */
    @JvmStatic
    @Throws(SecurityException::class)
    fun getAllPhoneNumbers(context: Context): List<LocalPhoneNumber> {
        // Each subscription is a different SIM card
        val subscriptionManager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        if (subscriptionManager == null) {
            // I don't know why or when this happens...
            Log.w(LOGGING_TAG, "Could not get SubscriptionManager")
            return emptyList()
        }
        val subscriptionInfos = subscriptionManager.activeSubscriptionInfoList
        if (subscriptionInfos == null) {
            // This happens when there is no SIM card inserted
            Log.w(LOGGING_TAG, "Could not get SubscriptionInfos")
            return emptyList()
        }
        return subscriptionInfos.mapNotNull { info ->
            val number = info.number
            if (number != null) {
                LocalPhoneNumber(number, info.subscriptionId)
            } else {
                null
            }
        }
    }

    /**
     * Try to get the phone number to which the TelephonyManager is pinned
     */
    @JvmStatic
    @Throws(SecurityException::class)
    fun getPhoneNumber(telephonyManager: TelephonyManager): LocalPhoneNumber? {
        @SuppressLint("HardwareIds")
        val maybeNumber = telephonyManager.line1Number

        if (maybeNumber == null) {
            Log.d(LOGGING_TAG, "Got 'null' instead of a phone number")
            return null
        }
        // Sometimes we will get some garbage like "Unknown" or "?????" or a variety of other things
        // Per https://stackoverflow.com/a/25131061/3723163, the only real solution to this is to
        // query the user for the proper phone number
        // As a quick possible check, I say if a "number" is not at least 25% digits, it is not actually
        // a number
        var digitCount = 0
        for (digit in "0123456789".toCharArray()) {
            // https://stackoverflow.com/a/8910767/3723163
            // The number of occurrences of a particular character can be counted by looking at the
            // total length of the string and subtracting the length of the string without the
            // target digit
            val count = maybeNumber.length - maybeNumber.replace("" + digit, "").length
            digitCount += count
        }
        if (maybeNumber.length > digitCount * 4) {
            Log.d(LOGGING_TAG, "Discarding $maybeNumber because it does not contain a high enough digit ratio to be a real phone number")
            return null
        } else {
            return LocalPhoneNumber(maybeNumber, -1)
        }
    }

    /**
     * Get the APN settings of the current APN for the given subscription ID
     *
     * Note that this method is broken after Android 4.2 but starts working again "at some point"
     * After Android 4.2, *reading* APN permissions requires a system permission (WRITE_APN_SETTINGS)
     * Before this, no permission is required
     * At some point after, the permission is not required to read non-sensitive columns (which are the
     * only ones we need)
     * If anyone has a solution to this (which doesn't involve a vendor-sepecific XML), feel free to share!
     *
     * Cobbled together from the [Android sources](https://android.googlesource.com/platform/packages/services/Mms/+/refs/heads/master/src/com/android/mms/service/ApnSettings.java)
     * and some StackOverflow Posts
     * [post 1](https://stackoverflow.com/a/18897139/3723163)
     * [post 2[(https://stackoverflow.com/a/7928751/3723163)
     *
     * @param context Context of the requester
     * @param subscriptionId Subscription ID for which to get the preferred APN. Ignored for devices older than Lollypop
     * @return Null if the preferred APN can't be found or doesn't support MMS, otherwise an ApnSetting object
     */
    @SuppressLint("InlinedApi")
    @JvmStatic
    fun getPreferredApn(context: Context, subscriptionId: Int): ApnSetting? {
        val apnProjection = arrayOf(
            Telephony.Carriers.TYPE,
            Telephony.Carriers.MMSC,
            Telephony.Carriers.MMSPROXY,
            Telephony.Carriers.MMSPORT,
        )

        val telephonyCarriersUri = Telephony.Carriers.CONTENT_URI
        val telephonyCarriersPreferredApnUri = Uri.withAppendedPath(telephonyCarriersUri, "/preferapn/subId/$subscriptionId")

        try {
            context.contentResolver.query(
                telephonyCarriersPreferredApnUri,
                apnProjection,
                null,
                null,
                Telephony.Carriers.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TYPE))
                    if (!isValidApnType(type, APN_TYPE_MMS)) continue

                    val apnBuilder = ApnSetting.Builder()
                        .setMmsc(Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))))
                        .setMmsProxyAddress(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY)))

                    val maybeMmsProxyPort = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT))
                    try {
                        val mmsProxyPort = maybeMmsProxyPort.toInt()
                        apnBuilder.setMmsProxyPort(mmsProxyPort)
                    } catch (_: Exception) {
                        // Lots of APN settings have other values, very commonly something like "Not set"
                        // just cross your fingers and hope that the default in ApnSetting works...
                        // If someone finds some documentation which says what the default value should be,
                        // please share
                    }

                    return apnBuilder.build()
                }
            }
        } catch (e: Exception) {
            Log.e(LOGGING_TAG, "Error encountered while trying to read APNs", e)
        }

        return null
    }

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     * Copied from Android's internal source: https://android.googlesource.com/platform/frameworks/base/+/cd92588/telephony/java/com/android/internal/telephony/PhoneConstants.java
     */
    private const val APN_TYPE_ALL = "*"
    /** APN type for MMS traffic */
    private const val APN_TYPE_MMS = "mms"

    /**
     * Copied directly from Android's source: https://android.googlesource.com/platform/packages/services/Mms/+/refs/heads/master/src/com/android/mms/service/ApnSettings.java
     * @param types Value of Telephony.Carriers.TYPE for the APN being interrogated
     * @param requestType Value which we would like to find in types
     * @return True if the APN supports the requested type, false otherwise
     */
    @JvmStatic
    fun isValidApnType(types: String?, requestType: String): Boolean {
        // If APN type is unspecified, assume APN_TYPE_ALL.
        if (types.isNullOrEmpty()) {
            return true
        }
        for (rawType in types.split(",")) {
            val type = rawType.trim()
            if (type == requestType || type == APN_TYPE_ALL) {
                return true
            }
        }
        return false
    }

    /**
     * Canonicalize a phone number by removing all (valid) non-digit characters
     *
     * Should be equivalent to SmsHelper::canonicalizePhoneNumber in the C++ implementation
     *
     * @param phoneNumber The phone number to canonicalize
     * @return The canonicalized version of the input phone number
     */
    @JvmStatic
    fun canonicalizePhoneNumber(phoneNumber: String): String {
        var toReturn = phoneNumber
        toReturn = toReturn.replace(" ", "")
        toReturn = toReturn.replace("-", "")
        toReturn = toReturn.replace("(", "")
        toReturn = toReturn.replace(")", "")
        toReturn = toReturn.replace("+", "")
        toReturn = toReturn.replaceFirst("^0*".toRegex(), "")

        if (toReturn.isEmpty()) {
            // If we have stripped away everything, assume this is a special number (and already canonicalized)
            return phoneNumber
        }
        return toReturn
    }

    /**
     * Light copy of https://developer.android.com/reference/android/telephony/data/ApnSetting so
     * that we can support older API versions. Delete this when API 28 becomes our supported version.
     */
    class ApnSetting private constructor(
        val mmsc: Uri? = null,
        val mmsProxyAddressAsString: String? = null,
        val mmsProxyPort: Int = 80 // Default port should be 80 according to code comment in Android's ApnSettings.java
    ) {
        class Builder {
            private var mmscUri: Uri? = null
            private var mmsProxyAddress: String? = null
            private var mmsProxyPort = 80

            fun setMmsc(mmscUri: Uri?): Builder {
                this.mmscUri = mmscUri
                return this
            }

            fun setMmsProxyAddress(mmsProxy: String?): Builder {
                this.mmsProxyAddress = mmsProxy
                return this
            }

            fun setMmsProxyPort(mmsPort: Int): Builder {
                this.mmsProxyPort = mmsPort
                return this
            }

            fun build(): ApnSetting {
                return ApnSetting(mmscUri, mmsProxyAddress, mmsProxyPort)
            }
        }
    }

    /**
     * Class representing a phone number which is assigned to the current device
     */
    data class LocalPhoneNumber(
        /**
         * The phone number
         */
        @JvmField val number: String,
        /**
         * The subscription ID to which this phone number belongs
         */
        @JvmField val subscriptionID: Int
    ) {
        override fun toString(): String = number

        /**
         * Do some basic fuzzy matching on two phone numbers to determine whether they match
         *
         * This is roughly equivalent to SmsHelper::isPhoneNumberMatch, but might produce more false negatives
         *
         * @param potentialMatchingPhoneNumber The phone number to compare to this phone number
         * @return True if the phone numbers appear to be the same, false otherwise
         */
        fun isMatchingPhoneNumber(potentialMatchingPhoneNumber: String): Boolean {
            val mPhoneNumber = canonicalizePhoneNumber(number)
            val oPhoneNumber = canonicalizePhoneNumber(potentialMatchingPhoneNumber)

            if (mPhoneNumber.isEmpty() || oPhoneNumber.isEmpty()) {
                // The empty string is not a valid phone number so does not match anything
                return false
            }

            // To decide if a phone number matches:
            // 1. Are they similar lengths? If two numbers are very different, probably one is junk data and should be ignored
            // 2. Is one a superset of the other? Phone number digits get more specific the further towards the end of the string,
            //    so if one phone number ends with the other, it is probably just a more-complete version of the same thing
            val longerNumber = if (mPhoneNumber.length >= oPhoneNumber.length) mPhoneNumber else oPhoneNumber
            val shorterNumber = if (mPhoneNumber.length < oPhoneNumber.length) mPhoneNumber else oPhoneNumber

            // If the numbers are vastly different in length, assume they are not the same
            if (shorterNumber.length < 0.75 * longerNumber.length) {
                return false
            }

            return longerNumber.endsWith(shorterNumber)
        }
    }
}
