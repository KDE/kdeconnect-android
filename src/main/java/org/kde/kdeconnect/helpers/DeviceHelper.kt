/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.content.edit
import com.univocity.parsers.common.TextParsingException
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

object DeviceHelper {
    const val PROTOCOL_VERSION = 8

    const val KEY_DEVICE_NAME_PREFERENCE = "device_name_preference"
    private const val KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET = "device_name_downloaded_preference"
    private const val KEY_DEVICE_ID_PREFERENCE = "device_id_preference"

    private var fetchingName = false

    private const val DEVICE_DATABASE = "https://storage.googleapis.com/play_public/supported_devices.csv"

    private val NAME_INVALID_CHARACTERS_REGEX = "[\"',;:.!?()\\[\\]<>]".toRegex()
    const val MAX_DEVICE_NAME_LENGTH = 32

    val isTablet: Boolean by lazy {
        val config = Resources.getSystem().configuration
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        ((config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
    }

    val isTv: Boolean by lazy {
        val uiMode = Resources.getSystem().configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    @JvmStatic
    val deviceType: DeviceType by lazy {
        if (isTv) {
            DeviceType.TV
        } else if (isTablet) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }

    @JvmStatic
    fun getDeviceName(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.contains(KEY_DEVICE_NAME_PREFERENCE)
            && !preferences.getBoolean(KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET, false)
            && !fetchingName
        ) {
            fetchingName = true
            backgroundFetchDeviceName(context)
            return Build.MODEL
        }
        return preferences.getString(KEY_DEVICE_NAME_PREFERENCE, Build.MODEL)!!
    }

    private fun backgroundFetchDeviceName(context: Context) {
        ThreadHelper.execute {
            try {
                val url = URL(DEVICE_DATABASE)
                val connection = url.openConnection()

                // If we get here we managed to download the file. Mark that as done so we don't try again even if we don't end up finding a name.
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                preferences.edit { putBoolean(KEY_DEVICE_NAME_FETCHED_FROM_THE_INTERNET, true) }

                BufferedReader(
                    InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_16)
                ).use { reader ->
                    val settings = CsvParserSettings()
                    settings.isHeaderExtractionEnabled = true
                    val parser = CsvParser(settings)
                    var found = false
                    for (records in parser.iterate(reader)) {
                        if (records.size < 4) {
                            continue
                        }
                        val buildModel = records[3]
                        if (Build.MODEL.equals(buildModel, ignoreCase = true)) {
                            val deviceName = records[1]
                            Log.i("DeviceHelper", "Got device name: $deviceName")
                            // Update the shared preference. Places that display the name should be listening to this change and update it
                            setDeviceName(context, deviceName)
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        Log.e("DeviceHelper", "Didn't find a device name for " + Build.MODEL)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: TextParsingException) {
                e.printStackTrace()
            }
            fetchingName = false
        }
    }

    fun setDeviceName(context: Context, name: String) {
        val filteredName = filterInvalidCharactersFromDeviceNameAndLimitLength(name)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit { putString(KEY_DEVICE_NAME_PREFERENCE, filteredName) }
    }

    fun initializeDeviceId(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val deviceId = preferences.getString(KEY_DEVICE_ID_PREFERENCE, "")!!
        if (DeviceInfo.isValidDeviceId(deviceId)) {
            return // We already have an ID
        }
        val deviceName = UUID.randomUUID().toString().replace("-", "")
        preferences.edit { putString(KEY_DEVICE_ID_PREFERENCE, deviceName) }
    }

    @JvmStatic
    fun getDeviceId(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(KEY_DEVICE_ID_PREFERENCE, null)!!
    }

    @JvmStatic
    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            getDeviceId(context),
            SslHelper.certificate,
            getDeviceName(context),
            deviceType,
            PROTOCOL_VERSION,
            PluginFactory.incomingCapabilities,
            PluginFactory.outgoingCapabilities
        )
    }

    @JvmStatic
    fun filterInvalidCharactersFromDeviceNameAndLimitLength(input: String): String = filterInvalidCharactersFromDeviceName(input).trim().take(MAX_DEVICE_NAME_LENGTH)

    @JvmStatic
    fun filterInvalidCharactersFromDeviceName(input: String): String = input.replace(NAME_INVALID_CHARACTERS_REGEX, "")

}
