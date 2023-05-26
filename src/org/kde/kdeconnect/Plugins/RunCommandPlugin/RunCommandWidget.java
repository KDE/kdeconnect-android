package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect_tp.R;

import java.util.concurrent.ConcurrentHashMap;

public class RunCommandWidget extends AppWidgetProvider {

    public static final String RUN_COMMAND_ACTION = "RUN_COMMAND_ACTION";
    public static final String TARGET_COMMAND = "TARGET_COMMAND";
    public static final String TARGET_DEVICE = "TARGET_DEVICE";
    private static final String SET_CURRENT_DEVICE = "SET_CURRENT_DEVICE";

    private static String currentDeviceId;

    @Override
    public void onReceive(Context context, Intent intent) {

        super.onReceive(context, intent);

        if (intent != null && intent.getAction() != null && intent.getAction().equals(RUN_COMMAND_ACTION)) {

            final String targetCommand = intent.getStringExtra(TARGET_COMMAND);
            final String targetDevice = intent.getStringExtra(TARGET_DEVICE);

            RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(targetDevice, RunCommandPlugin.class);

            if (plugin != null) {
                try {
                    plugin.runCommand(targetCommand);
                } catch (Exception ex) {
                    Log.e("RunCommandWidget", "Error running command", ex);
                }
            }
        } else if (intent != null && TextUtils.equals(intent.getAction(), SET_CURRENT_DEVICE)) {
            setCurrentDevice(context);
        }

        try {
            // FIXME: Remove the need for a background service, we are not allowed to start them in API 31+
            final Intent newIntent = new Intent(context, RunCommandWidgetDataProviderService.class);
            context.startService(newIntent);
            updateWidget(context);
        } catch(IllegalStateException exception) { // Meant to catch BackgroundServiceStartNotAllowedException on API 31+
            exception.printStackTrace();
        }
    }

    private void setCurrentDevice(final Context context) {
        Intent popup = new Intent(context, RunCommandWidgetDeviceSelector.class);
        popup.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        context.startActivity(popup);
    }

    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidget(context);
    }

    private void updateWidget(final Context context) {

        Device device = getCurrentDevice();

        if (device == null || !device.isReachable()) {
            ConcurrentHashMap<String, Device>  devices = KdeConnect.getInstance().getDevices();
            if (devices.size() > 0) {
                currentDeviceId = devices.elements().nextElement().getDeviceId();
            }
        }

        updateWidgetImpl(context);
    }

    private void updateWidgetImpl(Context context) {

        try {

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, RunCommandWidget.class));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_remotecommandplugin);

            PendingIntent pendingIntent;
            Intent intent;

            intent = new Intent(context, RunCommandWidget.class);
            intent.setAction(SET_CURRENT_DEVICE);
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setOnClickPendingIntent(R.id.runcommandWidgetTitleHeader, pendingIntent);

            Device device = getCurrentDevice();
            if (device == null || !device.isReachable()) {
                views.setTextViewText(R.id.runcommandWidgetTitle, context.getString(R.string.kde_connect));
                views.setViewVisibility(R.id.run_commands_list, View.GONE);
                views.setViewVisibility(R.id.not_reachable_message, View.VISIBLE);
            } else {
                views.setTextViewText(R.id.runcommandWidgetTitle, device.getName());
                views.setViewVisibility(R.id.run_commands_list, View.VISIBLE);
                views.setViewVisibility(R.id.not_reachable_message, View.GONE);
            }

            for (int appWidgetId : appWidgetIds) {

                intent = new Intent(context, RunCommandWidgetDataProviderService.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                views.setRemoteAdapter(R.id.run_commands_list, intent);

                intent = new Intent(context, RunCommandWidget.class);
                pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                views.setPendingIntentTemplate(R.id.run_commands_list, pendingIntent);

                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(appWidgetId, R.id.run_commands_list);

            }

        } catch (Exception ex) {
            Log.e("RunCommandWidget", "Error updating widget", ex);
        }


        KdeConnect.getInstance().addDeviceListChangedCallback("RunCommandWidget", () -> {
            Intent updateWidget = new Intent(context, RunCommandWidget.class);
            context.sendBroadcast(updateWidget);
        });
    }

    public static Device getCurrentDevice() {
        return KdeConnect.getInstance().getDevice(currentDeviceId);
    }

    public static void setCurrentDevice(final String DeviceId) {
        currentDeviceId = DeviceId;
    }
}