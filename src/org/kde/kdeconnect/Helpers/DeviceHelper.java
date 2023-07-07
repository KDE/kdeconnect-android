/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.univocity.parsers.common.TextParsingException;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.DeviceType;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.PluginFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class DeviceHelper {

    public static final int ProtocolVersion = 7;

    public static final String KEY_DEVICE_NAME_PREFERENCE = "device_name_preference";
    public static final String KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET = "device_name_downloaded_preference";
    public static final String KEY_DEVICE_ID_PREFERENCE = "device_id_preference";

    private static boolean fetchingName = false;

    public static final String DEVICE_DATABASE = "https://storage.googleapis.com/play_public/supported_devices.csv";

    private static boolean isTablet() {
        Configuration config = Resources.getSystem().getConfiguration();
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        return ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE);
    }

    private static boolean isTv(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode;
        return (uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static DeviceType getDeviceType(Context context) {
        if (isTv(context)) {
            return DeviceType.Tv;
        } else if (isTablet()) {
            return DeviceType.Tablet;
        } else {
            return DeviceType.Phone;
        }
    }

    public static String getDeviceName(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.contains(KEY_DEVICE_NAME_PREFERENCE)
                && !preferences.getBoolean(KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET, false)
                && !fetchingName) {
            fetchingName = true;
            DeviceHelper.backgroundFetchDeviceName(context);
            return Build.MODEL;
        }
        return preferences.getString(KEY_DEVICE_NAME_PREFERENCE, Build.MODEL);
    }

    private static void backgroundFetchDeviceName(final Context context) {
        ThreadHelper.execute(() -> {
            try {
                URL url = new URL(DEVICE_DATABASE);
                URLConnection connection = url.openConnection();

                // If we get here we managed to download the file. Mark that as done so we don't try again even if we don't end up finding a name.
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                preferences.edit().putBoolean(KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET, true).apply();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_16))) {
                    CsvParserSettings settings = new CsvParserSettings();
                    settings.setHeaderExtractionEnabled(true);
                    CsvParser parser = new CsvParser(settings);
                    boolean found = false;
                    for (String[] records : parser.iterate(reader)) {
                        if (records.length < 4) {
                            continue;
                        }
                        String buildModel = records[3];
                        if (Build.MODEL.equalsIgnoreCase(buildModel)) {
                            String deviceName = records[1];
                            Log.i("DeviceHelper", "Got device name: " + deviceName);
                            // Update the shared preference. Places that display the name should be listening to this change and update it
                            setDeviceName(context, deviceName);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        Log.e("DeviceHelper", "Didn't find a device name for " + Build.MODEL);
                    }
                }
            } catch(IOException | TextParsingException e) {
                e.printStackTrace();
            }
            fetchingName = false;
        });
    }

    public static void setDeviceName(Context context, String name) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(KEY_DEVICE_NAME_PREFERENCE, name).apply();
    }


    @SuppressLint("HardwareIds")
    public static void initializeDeviceId(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> preferenceKeys = preferences.getAll().keySet();
        if (preferenceKeys.contains(KEY_DEVICE_ID_PREFERENCE)) {
            return; // We already have an ID
        }
        String deviceName;
        if (preferenceKeys.isEmpty()) {
            // For new installations, use random IDs
            Log.i("DeviceHelper", "No device ID found and this looks like a new installation, creating a random ID");
            deviceName = UUID.randomUUID().toString().replace('-','_');
        } else {
            // Use the ANDROID_ID as device ID for existing installations, for backwards compatibility
            Log.i("DeviceHelper", "No device ID found but this seems an existing installation, using the Android ID");
            deviceName = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        preferences.edit().putString(KEY_DEVICE_ID_PREFERENCE, deviceName).apply();
    }

    public static String getDeviceId(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(KEY_DEVICE_ID_PREFERENCE, null);
    }

    public static DeviceInfo getDeviceInfo(Context context) {
        return new DeviceInfo(getDeviceId(context),
                SslHelper.certificate,
                getDeviceName(context),
                DeviceHelper.getDeviceType(context),
                ProtocolVersion,
                PluginFactory.getIncomingCapabilities(),
                PluginFactory.getOutgoingCapabilities());
    }

}
