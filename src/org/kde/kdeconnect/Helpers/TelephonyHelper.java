/*
 * SPDX-FileCopyrightText: 2019 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
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
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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
        if (subscriptionInfos == null) {
            // This happens when there is no SIM card inserted
            Log.w(LOGGING_TAG, "Could not get SubscriptionInfos");
            return Collections.emptyList();
        }
        List<Integer> subscriptionIDs = new ArrayList<>(subscriptionInfos.size());
        for (SubscriptionInfo info : subscriptionInfos) {
            subscriptionIDs.add(info.getSubscriptionId());
        }
        return subscriptionIDs;
    }

    /**
     * Callback for `listenActiveSubscriptionIDs`
     */
    public interface SubscriptionCallback {
        void run(Integer subscriptionID);
    }

    /**
     * Registers a listener for changes in subscriptionIDs for the device.
     * This lets you identify additions/removals of SIM cards.
     * Make sure to call `cancelActiveSubscriptionIDsListener` with the return value of this once you're done.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static OnSubscriptionsChangedListener listenActiveSubscriptionIDs(
            @NonNull Context context, SubscriptionCallback onAdd, SubscriptionCallback onRemove) {
        SubscriptionManager sm = ContextCompat.getSystemService(context, SubscriptionManager.class);
        if (sm == null) {
            // I don't know why or when this happens...
            Log.w(LOGGING_TAG, "Could not get SubscriptionManager");
            return null;
        }

        HashSet<Integer> activeIDs = new HashSet<>();

        OnSubscriptionsChangedListener listener = new OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                HashSet<Integer> nextSubs = new HashSet<>(getActiveSubscriptionIDs(context));

                HashSet<Integer> addedSubs = new HashSet<>(nextSubs);
                addedSubs.removeAll(activeIDs);

                HashSet<Integer> removedSubs = new HashSet<>(activeIDs);
                removedSubs.removeAll(nextSubs);

                activeIDs.removeAll(removedSubs);
                activeIDs.addAll(addedSubs);

                // Delete old listeners
                for (Integer subID : removedSubs) {
                    onRemove.run(subID);
                }

                // Create new listeners
                for (Integer subID : addedSubs) {
                    onAdd.run(subID);
                }
            }
        };
        sm.addOnSubscriptionsChangedListener(listener);
        return listener;
    }

    /**
     * Cancels a listener created by `listenActiveSubscriptionIDs`
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static void cancelActiveSubscriptionIDsListener(@NonNull Context context, @NonNull OnSubscriptionsChangedListener listener) {
        SubscriptionManager sm = ContextCompat.getSystemService(context, SubscriptionManager.class);
        if (sm == null) {
            // I don't know why or when this happens...
            Log.w(LOGGING_TAG, "Could not get SubscriptionManager");
            return;
        }

        sm.removeOnSubscriptionsChangedListener(listener);
    }

    /**
     * Try to get the phone number currently active on the phone
     *
     * Make sure that you have the READ_PHONE_STATE permission!
     *
     * Note that entries of the returned list might return null if the phone number is not known by the device
     */
    public static @NonNull List<LocalPhoneNumber> getAllPhoneNumbers(
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
            LocalPhoneNumber phoneNumber = getPhoneNumber(telephonyManager);
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
            List<LocalPhoneNumber> phoneNumbers = new ArrayList<>(subscriptionInfos.size());
            for (SubscriptionInfo info : subscriptionInfos) {
                LocalPhoneNumber thisPhoneNumber = new LocalPhoneNumber(info.getNumber(), info.getSubscriptionId());
                phoneNumbers.add(thisPhoneNumber);
            }
            return phoneNumbers.stream().filter(localPhoneNumber -> localPhoneNumber.number != null).collect(Collectors.toList());
        }
    }

    /**
     * Try to get the phone number to which the TelephonyManager is pinned
     */
    public static @Nullable LocalPhoneNumber getPhoneNumber(
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
            return new LocalPhoneNumber(maybeNumber, -1);
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
     * Canonicalize a phone number by removing all (valid) non-digit characters
     *
     * Should be equivalent to SmsHelper::canonicalizePhoneNumber in the C++ implementation
     *
     * @param phoneNumber The phone number to canonicalize
     * @return The canonicalized version of the input phone number
     */
    public static String canonicalizePhoneNumber(String phoneNumber)
    {
        String toReturn = phoneNumber;
        toReturn = toReturn.replace(" ", "");
        toReturn = toReturn.replace("-", "");
        toReturn = toReturn.replace("(", "");
        toReturn = toReturn.replace(")", "");
        toReturn = toReturn.replace("+", "");
        toReturn = toReturn.replaceFirst("^0*", "");

        if (toReturn.isEmpty()) {
            // If we have stripped away everything, assume this is a special number (and already canonicalized)
            return phoneNumber;
        }
        return toReturn;
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

    /**
     * Class representing a phone number which is assigned to the current device
     */
    public static class LocalPhoneNumber {
        /**
        * The phone number
        */
        public final String number;

        /**
         * The subscription ID to which this phone number belongs
         */
        public final int subscriptionID;

       public LocalPhoneNumber(String number, int subscriptionID) {
           this.number = number;
           this.subscriptionID = subscriptionID;
       }

        @Override
        public String toString() {
            return number;
        }

        /**
         * Do some basic fuzzy matching on two phone numbers to determine whether they match
         *
         * This is roughly equivalent to SmsHelper::isPhoneNumberMatch, but might produce more false negatives
         *
         * @param potentialMatchingPhoneNumber The phone number to compare to this phone number
         * @return True if the phone numbers appear to be the same, false otherwise
         */
        public boolean isMatchingPhoneNumber(String potentialMatchingPhoneNumber) {
           String mPhoneNumber = canonicalizePhoneNumber(this.number);
           String oPhoneNumber = canonicalizePhoneNumber(potentialMatchingPhoneNumber);

            if (mPhoneNumber.isEmpty() || oPhoneNumber.isEmpty()) {
                // The empty string is not a valid phone number so does not match anything
                return false;
            }

            // To decide if a phone number matches:
            // 1. Are they similar lengths? If two numbers are very different, probably one is junk data and should be ignored
            // 2. Is one a superset of the other? Phone number digits get more specific the further towards the end of the string,
            //    so if one phone number ends with the other, it is probably just a more-complete version of the same thing
            String longerNumber = mPhoneNumber.length() >= oPhoneNumber.length() ? mPhoneNumber : oPhoneNumber;
            String shorterNumber = mPhoneNumber.length() < oPhoneNumber.length() ? mPhoneNumber : oPhoneNumber;

            // If the numbers are vastly different in length, assume they are not the same
            if (shorterNumber.length() < 0.75 * longerNumber.length()) {
                return false;
            }

            boolean matchingPhoneNumber = longerNumber.endsWith(shorterNumber);

            return matchingPhoneNumber;
        }
    }
}
