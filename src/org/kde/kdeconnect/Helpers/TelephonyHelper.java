/*
 * Copyright 2019 Simon Redman <simon@ergotech.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelephonyHelper {

    public static final String LOGGING_TAG = "TelephonyHelper";

    /**
     * Get all subscriptionIDs of the device
     * As far as I can tell, this is essentially a way of identifying particular SIM cards
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static List<Integer> getActiveSubscriptionIDs(
            @NonNull Context context)
            throws SecurityException {
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager == null) {
            // I don't know why or when this happens...
            Log.w(LOGGING_TAG, "Could not get SubscriptionManager");
            return Collections.emptyList();
        }
        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
        List<Integer> subscriptionIDs = new ArrayList<>(subscriptionInfos.size());
        for (SubscriptionInfo info : subscriptionInfos) {
            subscriptionIDs.add(info.getSubscriptionId());
        }
        return subscriptionIDs;
    }

    /**
     * Try to get the phone number currently active on the phone
     *
     * Make sure that you have the READ_PHONE_STATE permission!
     *
     * Note that entries of the returned list might return null if the phone number is not known by the device
     */
    public static @NonNull List<String> getAllPhoneNumbers(
            @NonNull Context context)
            throws SecurityException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            // Single-sim case
            // From https://stackoverflow.com/a/25131061/3723163
            // Android added support for multi-sim devices in Lollypop v5.1 (api 22)
            // See: https://developer.android.com/about/versions/android-5.1.html#multisim
            // There were vendor-specific implmentations before then, but those are very difficult to support
            // S/O Reference: https://stackoverflow.com/a/28571835/3723163
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                // I don't know why or when this happens...
                Log.w(LOGGING_TAG, "Could not get TelephonyManager");
                return Collections.emptyList();
            }
            String phoneNumber = getPhoneNumber(telephonyManager);
            return Collections.singletonList(phoneNumber);
        } else {
            // Potentially multi-sim case
            SubscriptionManager subscriptionManager = (SubscriptionManager)context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager == null) {
                // I don't know why or when this happens...
                Log.w(LOGGING_TAG, "Could not get SubscriptionManager");
                return Collections.emptyList();
            }
            List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
            List<String> phoneNumbers = new ArrayList<>(subscriptionInfos.size());
            for (SubscriptionInfo info : subscriptionInfos) {
                phoneNumbers.add(info.getNumber());
            }
            return phoneNumbers;
        }
    }

    /**
     * Try to get the phone number to which the TelephonyManager is pinned
     */
    public static @Nullable String getPhoneNumber(
            @NonNull TelephonyManager telephonyManager)
            throws SecurityException {
        @SuppressLint("HardwareIds")
        String maybeNumber = telephonyManager.getLine1Number();

        if (maybeNumber == null) {
            Log.d(LOGGING_TAG, "Got 'null' instead of a phone number");
            return null;
        }
        // Sometimes we will get some garbage like "Unknown" or "?????" or a variety of other things
        // Per https://stackoverflow.com/a/25131061/3723163, the only real solution to this is to
        // query the user for the proper phone number
        // As a quick possible check, I say if a "number" is not at least 25% digits, it is not actually
        // a number
        int digitCount = 0;
        for (char digit : "0123456789".toCharArray()) {
            // https://stackoverflow.com/a/8910767/3723163
            // The number of occurrences of a particular character can be counted by looking at the
            // total length of the string and subtracting the length of the string without the
            // target digit
            int count = maybeNumber.length() - maybeNumber.replace("" + digit, "").length();
            digitCount += count;
        }
        if (maybeNumber.length() > digitCount*4) {
            Log.d(LOGGING_TAG, "Discarding " + maybeNumber + " because it does not contain a high enough digit ratio to be a real phone number");
            return null;
        } else {
            return maybeNumber;
        }
    }
}
