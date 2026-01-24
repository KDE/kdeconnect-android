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
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R

internal class RunCommandWidgetDataProvider(private val context: Context, val intent: Intent?) : RemoteViewsFactory {

    private var deviceId : String? = null
    private var widgetId : Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate() {
        widgetId = intent?.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("KDEConnect/Widget", "RunCommandWidgetDataProvider: No widget id extra was set")
            return
        }
        deviceId = loadWidgetDeviceIdPref(context, widgetId)
    }

    override fun onDataSetChanged() {
        deviceId = loadWidgetDeviceIdPref(context, widgetId)
    }

    override fun onDestroy() {}

    private fun getPlugin(): RunCommandPlugin? {
        return KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin::class.java)
    }

    override fun getCount(): Int {
        return getPlugin()?.commandItems?.size ?: 0
    }

    override fun getViewAt(i: Int): RemoteViews {
        val remoteView = RemoteViews(context.packageName, R.layout.list_item_entry)

        val plugin : RunCommandPlugin? = getPlugin()
        if (plugin == null) {
            // Either the deviceId was null, or the plugin is not available.
            if (deviceId != null) {
                Log.e("getViewAt", "RunCommandWidgetDataProvider: Plugin not found")
            }
            // Return a new, not-configured layout as a fallback
            return remoteView
        }

        val listItem = plugin.commandItems[i]

        remoteView.setTextViewText(R.id.list_item_entry_title, listItem.name)
        remoteView.setTextViewText(R.id.list_item_entry_summary, listItem.command)
        remoteView.setViewVisibility(R.id.list_item_entry_summary, View.VISIBLE)

        val runCommandIntent = Intent(context, RunCommandWidgetProvider::class.java)
        runCommandIntent.action = RUN_COMMAND_ACTION
        runCommandIntent.putExtra(EXTRA_APPWIDGET_ID, widgetId)
        runCommandIntent.putExtra(TARGET_COMMAND, listItem.key)
        runCommandIntent.putExtra(TARGET_DEVICE, deviceId)
        remoteView.setOnClickFillInIntent(R.id.list_item_entry, runCommandIntent)

        return remoteView
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(i: Int): Long {
        return getPlugin()?.commandItems?.getOrNull(i)?.key?.hashCode()?.toLong() ?: 0
    }

    override fun hasStableIds(): Boolean {
        return false
    }
}

class CommandsRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RunCommandWidgetDataProvider(this.applicationContext, intent)
    }
}

