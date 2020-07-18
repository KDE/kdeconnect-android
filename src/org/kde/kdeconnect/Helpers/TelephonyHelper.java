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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

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
        SubscriptionManager subscriptionManager = ContextCompat.getSystemService(context,
                SubscriptionManager.class);
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
            TelephonyManager telephonyManager = ContextCompat.getSystemService(context,
                    TelephonyManager.class);
            if (telephonyManager == null) {
                // I don't know why or when this happens...
                Log.w(LOGGING_TAG, "Could not get TelephonyManager");
                return Collections.emptyList();
            }
            String phoneNumber = getPhoneNumber(telephonyManager);
            return Collections.singletonList(phoneNumber);
        } else {
            // Potentially multi-sim case
            SubscriptionManager subscriptionManager = ContextCompat.getSystemService(context,
                    SubscriptionManager.class);
            if (subscriptionManager == null) {
                // I don't know why or when this happens...
                Log.w(LOGGING_TAG, "Could not get SubscriptionManager");
                return Collections.emptyList();
            }
            List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfos == null) {
                // This happens when there is no SIM card inserted
                Log.w(LOGGING_TAG, "Could not get SubscriptionInfos");
                return Collections.emptyList();
            }
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
     * @param context Context of the requestor
     * @param subscriptionId Subscription ID for which to get the preferred APN. Ignored for devices older than Lollypop
     * @return Null if the preferred APN can't be found or doesn't support MMS, otherwise an ApnSetting object
     */
    @SuppressLint("InlinedApi")
    public static ApnSetting getPreferredApn(Context context, int subscriptionId) {

        String[] APN_PROJECTION = {
                Telephony.Carriers.TYPE,
                Telephony.Carriers.MMSC,
                Telephony.Carriers.MMSPROXY,
                Telephony.Carriers.MMSPORT,
        };

        Uri telephonyCarriersUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            telephonyCarriersUri = Telephony.Carriers.CONTENT_URI;
        } else {
            // This is provided in the optimistic hope that it will "just work" for older devices
            // content:// URI from Telephony.Carriers source:
            // https://android.googlesource.com/platform/frameworks/opt/telephony/+/27bc967ba840d2e2a8941d60aef89d0cb80b1626/src/java/android/provider/Telephony.java
            telephonyCarriersUri = Uri.parse("content://telephony/carriers");
        }

        Uri telephonyCarriersPreferredApnUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            telephonyCarriersPreferredApnUri = Uri.withAppendedPath(telephonyCarriersUri, "/preferapn/subId/" + subscriptionId);
        } else {
            // Ignore subID for devices before that existed
            telephonyCarriersPreferredApnUri = Uri.withAppendedPath(telephonyCarriersUri, "/preferapn/");
        }

        try (Cursor cursor = context.getContentResolver().query(
                telephonyCarriersPreferredApnUri,
                APN_PROJECTION,
                null,
                null,
                Telephony.Carriers.DEFAULT_SORT_ORDER
        )) {
            while (cursor != null && cursor.moveToNext()) {

                String type = cursor.getString(cursor.getColumnIndex(Telephony.Carriers.TYPE));
                if (!isValidApnType(type, APN_TYPE_MMS)) continue;

                ApnSetting.Builder apnBuilder = new ApnSetting.Builder()
                        .setMmsc(Uri.parse(cursor.getString(cursor.getColumnIndex(Telephony.Carriers.MMSC))))
                        .setMmsProxyAddress(cursor.getString(cursor.getColumnIndex(Telephony.Carriers.MMSPROXY)));

                String maybeMmsProxyPort = cursor.getString(cursor.getColumnIndex(Telephony.Carriers.MMSPORT));
                try {
                    int mmsProxyPort = Integer.parseInt(maybeMmsProxyPort);
                    apnBuilder.setMmsProxyPort(mmsProxyPort);
                } catch (Exception e) {
                    // Lots of APN settings have other values, very commonly something like "Not set"
                    // just cross your fingers and hope that the default in ApnSetting works...
                    // If someone finds some documentation which says what the default value should be,
                    // please share
                }

                return apnBuilder.build();
            }
        } catch (Exception e)
        {
            Log.e(LOGGING_TAG, "Error encountered while trying to read APNs", e);
        }

        return null;
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
    private static final String APN_TYPE_ALL = "*";
    /** APN type for MMS traffic */
    private static final String APN_TYPE_MMS = "mms";

    /**
     * Copied directly from Android's source: https://android.googlesource.com/platform/packages/services/Mms/+/refs/heads/master/src/com/android/mms/service/ApnSettings.java
     * @param types Value of Telephony.Carriers.TYPE for the APN being interrogated
     * @param requestType Value which we would like to find in types
     * @return True if the APN supports the requested type, false otherwise
     */
    private static boolean isValidApnType(String types, String requestType) {
        // If APN type is unspecified, assume APN_TYPE_ALL.
        if (types.isEmpty()) {
            return true;
        }
        for (String type : types.split(",")) {
            type = type.trim();
            if (type.equals(requestType) || type.equals(APN_TYPE_ALL)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Light copy of https://developer.android.com/reference/android/telephony/data/ApnSetting so
     * that we can support older API versions. Delete this when API 28 becomes our supported version.
     */
    public static class ApnSetting
    {
        private Uri mmscUri = null;
        private String mmsProxyAddress = null;
        private int mmsProxyPort = 80; // Default port should be 80 according to code comment in Android's ApnSettings.java

        public static class Builder {
            private org.kde.kdeconnect.Helpers.TelephonyHelper.ApnSetting internalApnSetting;

            public Builder() {
                internalApnSetting = new ApnSetting();
            }

            public Builder setMmsc(Uri mmscUri) {
                internalApnSetting.mmscUri = mmscUri;
                return this;
            }

            public Builder setMmsProxyAddress(String mmsProxy) {
                internalApnSetting.mmsProxyAddress = mmsProxy;
                return this;
            }

            public Builder setMmsProxyPort(int mmsPort) {
                internalApnSetting.mmsProxyPort = mmsPort;
                return this;
            }

            public ApnSetting build() {
                return internalApnSetting;
            }
        }

        private ApnSetting() {};

        public Uri getMmsc() {
            return mmscUri;
        }

        public String getMmsProxyAddressAsString() {
            return mmsProxyAddress;
        }

        public int getMmsProxyPort() {
            return mmsProxyPort;
        }
    }
}
