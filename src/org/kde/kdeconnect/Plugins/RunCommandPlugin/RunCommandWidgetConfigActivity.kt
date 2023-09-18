/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.RunCommandPlugin

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.WidgetRemoteCommandPluginDialogBinding
import java.util.stream.Collectors
import kotlin.streams.toList

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

        val pairedDevices = KdeConnect.getInstance().devices.values.stream().filter(Device::isPaired).collect(Collectors.toList());

        binding.runCommandsDeviceList.adapter = object : ArrayAdapter<Device>(this, 0, pairedDevices) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view : View = convertView ?: layoutInflater.inflate(R.layout.list_item_with_icon_entry, parent, false)
                val device = pairedDevices[position]
                view.findViewById<TextView>(R.id.list_item_entry_title).text = device.name
                view.findViewById<ImageView>(R.id.list_item_entry_icon).setImageDrawable(device.icon)
                return view
            }
        }
        binding.runCommandsDeviceList.setOnItemClickListener  { _, _, position, _ ->
            val deviceId = pairedDevices[position].deviceId
            saveWidgetDeviceIdPref(this, appWidgetId, deviceId)

            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateAppWidget(this, appWidgetManager, appWidgetId)

            val resultValue = Intent()
            resultValue.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
        binding.runCommandsDeviceList.emptyView = binding.noDevices
    }
}

private const val PREFS_NAME = "org.kde.kdeconnect_tp.WidgetProvider"
private const val PREF_PREFIX_KEY = "appwidget_"

internal fun saveWidgetDeviceIdPref(context: Context, appWidgetId: Int, deviceName: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.putString(PREF_PREFIX_KEY + appWidgetId, deviceName)
    prefs.apply()
}

internal fun loadWidgetDeviceIdPref(context: Context, appWidgetId: Int): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
}

internal fun deleteWidgetDeviceIdPref(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.remove(PREF_PREFIX_KEY + appWidgetId)
    prefs.apply()
}
