/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.customdevices.CustomDevicesRoute

class CustomDevicesActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            KdeTheme(this) {
                CustomDevicesRoute(
                    onNavigateUp = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }

    companion object {
        const val KEY_CUSTOM_DEVICE_LIST_PREFERENCE = "device_list_preference"
        const val IP_DELIM = ","

        @JvmStatic
        fun getCustomDeviceList(context: Context): ArrayList<DeviceHost> {
            val prefs = context.getSharedPreferences(context.packageName + "_preferences", MODE_PRIVATE)
            val raw = prefs.getString(KEY_CUSTOM_DEVICE_LIST_PREFERENCE, "") ?: ""
            val list = ArrayList<DeviceHost>()
            if (raw.isNotEmpty()) {
                for (entry in raw.split(IP_DELIM)) {
                    val host = DeviceHost.toDeviceHostOrNull(entry)
                    if (host != null) list.add(host)
                }
            }
            list.sortWith(compareBy { it.toString() })
            return list
        }
    }
}
