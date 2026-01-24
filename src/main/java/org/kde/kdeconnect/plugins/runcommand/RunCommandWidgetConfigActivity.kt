/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.runcommand

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.list.DeviceItem
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect_tp.databinding.WidgetRemoteCommandPluginDialogBinding

class RunCommandWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setResult(RESULT_CANCELED) // Default result

        appWidgetId = intent.extras?.getInt(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

        val binding = WidgetRemoteCommandPluginDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pairedDevices = KdeConnect.getInstance().devices.values.asSequence().filter(Device::isPaired).toList()

        val list = ListAdapter(this, pairedDevices.map { DeviceItem(it, ::deviceClicked) })
        binding.runCommandsDeviceList.adapter = list
        binding.runCommandsDeviceList.emptyView = binding.noDevices
    }

    fun deviceClicked(device: Device) {
        val deviceId = device.deviceId
        saveWidgetDeviceIdPref(this, appWidgetId, deviceId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

private const val PREFS_NAME = "org.kde.kdeconnect_tp.WidgetProvider"
private const val PREF_PREFIX_KEY = "appwidget_"

internal fun saveWidgetDeviceIdPref(context: Context, appWidgetId: Int, deviceName: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(PREF_PREFIX_KEY + appWidgetId, deviceName)
    }
}

internal fun loadWidgetDeviceIdPref(context: Context, appWidgetId: Int): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
}

internal fun deleteWidgetDeviceIdPref(context: Context, appWidgetId: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        remove(PREF_PREFIX_KEY + appWidgetId)
    }
}
