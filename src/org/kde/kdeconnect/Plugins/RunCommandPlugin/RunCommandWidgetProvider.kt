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
import android.view.View
import android.widget.RemoteViews
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

const val RUN_COMMAND_ACTION = "RUN_COMMAND_ACTION"
const val TARGET_COMMAND = "TARGET_COMMAND"
const val TARGET_DEVICE = "TARGET_DEVICE"
private const val SET_CURRENT_DEVICE = "SET_CURRENT_DEVICE"

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
        super.onReceive(context, intent)
        Log.d("WidgetProvider", "onReceive " + intent.action)

        val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("KDEConnect/Widget", "No widget id extra set")
            return
        }

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
            }
        } else if (intent.action == SET_CURRENT_DEVICE) {
            val popup = Intent(context, RunCommandWidgetConfig::class.java)
            popup.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            popup.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            context.startActivity(popup)
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

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    Log.d("WidgetProvider", "updateAppWidget")

    val deviceId = loadWidgetDeviceIdPref(context, appWidgetId)
    val device: Device? = if (deviceId != null) KdeConnect.getInstance().getDevice(deviceId) else null

    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_remotecommandplugin)
    val setDeviceIntent = Intent(context, RunCommandWidgetProvider::class.java)
    setDeviceIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
    setDeviceIntent.action = SET_CURRENT_DEVICE
    val setDevicePendingIntent = PendingIntent.getBroadcast(context,0,setDeviceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    views.setOnClickPendingIntent(R.id.runcommandWidgetTitleHeader, setDevicePendingIntent)

    if (device == null) {
        views.setTextViewText(R.id.runcommandWidgetTitle, context.getString(R.string.kde_connect))
        views.setViewVisibility(R.id.run_commands_list, View.VISIBLE)
        views.setViewVisibility(R.id.not_reachable_message, View.GONE)
    } else {
        views.setTextViewText(R.id.runcommandWidgetTitle, device.name)
        if (device.isReachable) {
            views.setViewVisibility(R.id.run_commands_list, View.VISIBLE)
            views.setViewVisibility(R.id.not_reachable_message, View.GONE)
            // Configure remote adapter
            val dataProviderIntent = Intent(context, CommandsRemoteViewsService::class.java)
            dataProviderIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            dataProviderIntent.data = Uri.parse(dataProviderIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.run_commands_list, dataProviderIntent)
            // This pending intent allows the remote adapter to call fillInIntent so list items can do things
            val runCommandTemplateIntent = Intent(context, RunCommandWidgetProvider::class.java)
            runCommandTemplateIntent.action = RUN_COMMAND_ACTION
            runCommandTemplateIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            val runCommandTemplatePendingIntent = PendingIntent.getBroadcast(context, 0, runCommandTemplateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            views.setPendingIntentTemplate(R.id.run_commands_list, runCommandTemplatePendingIntent)
        } else {
            views.setViewVisibility(R.id.run_commands_list, View.GONE)
            views.setViewVisibility(R.id.not_reachable_message, View.VISIBLE)
        }
    }

    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.run_commands_list)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
