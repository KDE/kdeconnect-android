/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.RunCommandPlugin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

const val RUN_COMMAND_ACTION = "RUN_COMMAND_ACTION"
const val TARGET_COMMAND = "TARGET_COMMAND"
const val TARGET_DEVICE = "TARGET_DEVICE"

class RunCommandWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteWidgetDeviceIdPref(context, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        KdeConnect.getInstance().addDeviceListChangedCallback("RunCommandWidget") {
            forceRefreshWidgets(context)
        }
    }

    override fun onDisabled(context: Context) {
        KdeConnect.getInstance().removeDeviceListChangedCallback("RunCommandWidget")
        super.onDisabled(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WidgetProvider", "onReceive " + intent.action)

        if (intent.action == RUN_COMMAND_ACTION) {
            val targetCommand = intent.getStringExtra(TARGET_COMMAND)
            val targetDevice = intent.getStringExtra(TARGET_DEVICE)
            val plugin = KdeConnect.getInstance().getDevicePlugin(targetDevice, RunCommandPlugin::class.java)
            if (plugin != null) {
                try {
                    plugin.runCommand(targetCommand)
                } catch (ex: Exception) {
                    Log.e("RunCommandWidget", "Error running command", ex)
                }
            } else {
                Log.w("RunCommandWidget", "Device not available or runcommand plugin disabled");
            }
        } else {
            super.onReceive(context, intent);
        }
    }
}

fun getAllWidgetIds(context : Context) : IntArray {
    return AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, RunCommandWidgetProvider::class.java)
    )
}

fun forceRefreshWidgets(context : Context) {
    val intent = Intent(context, RunCommandWidgetProvider::class.java)
    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, getAllWidgetIds(context))
    context.sendBroadcast(intent)
}

/**
 * Recreate the [RemoteViews] layout of a given widget.
 *
 * This function is called when a new widget is created, or when the list of devices changes, or if
 * a device enables/disables its [RunCommandPlugin]. Hosting apps that contain our widgets will do
 * anything they can to avoid extra renders.
 *
 * 1. We use [appWidgetId] as a request code in [assignTitleIntent] to force hosting apps to track a
 *    separate intent for each widget.
 * 2. We call [AppWidgetManager.notifyAppWidgetViewDataChanged] at the end of this function, which
 *    lets the list adapter know that it might be referring to the wrong device id.
 *
 * See also [RunCommandWidgetDataProvider.onDataSetChanged].
 */
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    Log.d("WidgetProvider", "updateAppWidget: $appWidgetId")

    // Determine which device provided these commands
    val deviceId = loadWidgetDeviceIdPref(context, appWidgetId)
    val device: Device? = if (deviceId != null) KdeConnect.getInstance().getDevice(deviceId) else null

    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_remotecommandplugin)
    assignTitleIntent(context, appWidgetId, views)

    Log.d("WidgetProvider", "updateAppWidget device: " + if (device == null) "null" else device.name)

    // Android should automatically toggle between the command list and the error text
    views.setEmptyView(R.id.widget_command_list, R.id.widget_error_text)

    // TODO: Use string resources

    if (device == null) {
        // There are two reasons we reach this condition:
        // 1. there is no preference string for this widget id
        // 2. the string id does not match any devices in KdeConnect.getInstance()
        // In both cases, we want the user to assign a device to this widget
        views.setTextViewText(R.id.widget_title_text, context.getString(R.string.kde_connect))
        views.setTextViewText(R.id.widget_error_text, "Whose commands should we show? Click the title to set a device.")
    } else {
        views.setTextViewText(R.id.widget_title_text, device.name)
        val plugin = device.getPlugin(RunCommandPlugin::class.java)
        if (device.isReachable) {
            val message: String = if (plugin == null) {
                "Device doesn't allow us to run commands."
            } else {
                "Device has no commands available."
            }
            views.setTextViewText(R.id.widget_error_text, message)
            assignListAdapter(context, appWidgetId, views)
            assignListIntent(context, appWidgetId, views)
        } else {
            views.setTextViewText(R.id.widget_error_text, context.getString(R.string.runcommand_notreachable))
        }
    }

    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_command_list)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Create an Intent to launch the config activity whenever the title is clicked.
 *
 * See [RunCommandWidgetConfigActivity].
 */
private fun assignTitleIntent(context: Context, appWidgetId: Int, views: RemoteViews) {
    val setDeviceIntent = Intent(context, RunCommandWidgetConfigActivity::class.java)
    setDeviceIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
    // We pass appWidgetId as requestCode even if it's not used to force the creation a new PendingIntent
    // instead of reusing an existing one, which is what happens if only the "extras" field differs.
    // Docs: https://developer.android.com/reference/android/app/PendingIntent.html
    val setDevicePendingIntent = PendingIntent.getActivity(context, appWidgetId, setDeviceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widget_title_wrapper, setDevicePendingIntent)
}

/**
 * Configure remote adapter
 *
 * This function can only be called once in the lifetime of the widget. Subsequent calls do nothing.
 * Use [RunCommandWidgetConfigActivity] and the config function [saveWidgetDeviceIdPref] to change
 * the adapter's behavior.
 */
private fun assignListAdapter(context: Context, appWidgetId: Int, views: RemoteViews) {
    val dataProviderIntent = Intent(context, CommandsRemoteViewsService::class.java)
    dataProviderIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
    dataProviderIntent.data = Uri.parse(dataProviderIntent.toUri(Intent.URI_INTENT_SCHEME))
    views.setRemoteAdapter(R.id.widget_command_list, dataProviderIntent)
}

/**
 * This pending intent allows the remote adapter to call fillInIntent so list items can do things.
 *
 * See [RemoteViews.setOnClickFillInIntent].
 */
private fun assignListIntent(context: Context, appWidgetId: Int, views: RemoteViews) {
    val runCommandTemplateIntent = Intent(context, RunCommandWidgetProvider::class.java)
    runCommandTemplateIntent.action = RUN_COMMAND_ACTION
    runCommandTemplateIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
    val runCommandTemplatePendingIntent = PendingIntent.getBroadcast(context, appWidgetId, runCommandTemplateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    views.setPendingIntentTemplate(R.id.widget_command_list, runCommandTemplatePendingIntent)
}
