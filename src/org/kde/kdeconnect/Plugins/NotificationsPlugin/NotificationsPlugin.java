/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MaterialActivity;
import org.kde.kdeconnect.UserInterface.SettingsActivity;
import org.kde.kdeconnect_tp.R;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    public final static String PACKAGE_TYPE_NOTIFICATION = "kdeconnect.notification";
    public final static String PACKAGE_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";

/*
    private boolean sendIcons = false;
*/

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_notifications);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_notifications_desc);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public void startPreferencesActivity(final SettingsActivity parentActivity) {
        if (hasPermission()) {
            Intent intent = new Intent(parentActivity, NotificationFilterActivity.class);
            parentActivity.startActivity(intent);
        } else {
            getErrorDialog(parentActivity).show();
        }
    }

    private boolean hasPermission() {
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return (notificationListenerList != null && notificationListenerList.contains(context.getPackageName()));
    }

    @Override
    public boolean onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (hasPermission()) {
                NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                    @Override
                    public void onServiceStart(NotificationReceiver service) {
                        try {
                            service.addListener(NotificationsPlugin.this);
                            StatusBarNotification[] notifications = service.getActiveNotifications();
                            for (StatusBarNotification notification : notifications) {
                                sendNotification(notification, true);
                            }
                        } catch (Exception e) {
                            Log.e("NotificationsPlugin", "Exception");
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                @Override
                public void onServiceStart(NotificationReceiver service) {
                    service.removeListener(NotificationsPlugin.this);
                }
            });
    }


    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        String id = getNotificationKeyCompat(statusBarNotification);
        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_NOTIFICATION);
        np.set("id", id);
        np.set("isCancel", true);
        device.sendPackage(np);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        sendNotification(statusBarNotification, false);
    }

    public void sendNotification(StatusBarNotification statusBarNotification, boolean requestAnswer) {

        Notification notification = statusBarNotification.getNotification();
        AppDatabase appDatabase = new AppDatabase(context);

        if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0) {
            //This is not a notification we want!
            return;
        }

        appDatabase.open();
        if (!appDatabase.isEnabled(statusBarNotification.getPackageName())){
            return;
            // we dont want notification from this app
        }
        appDatabase.close();

        String key = getNotificationKeyCompat(statusBarNotification);
        String packageName = statusBarNotification.getPackageName();
        String appName = AppsHelper.appNameLookup(context, packageName);

        if ("com.facebook.orca".equals(packageName) &&
                (statusBarNotification.getId() == 10012) &&
                "Messenger".equals(appName) &&
                notification.tickerText == null) {
            //HACK: Hide weird Facebook empty "Messenger" notification that is actually not shown in the phone
            return;
        }

        if ("com.android.systemui".equals(packageName) &&
                "low_battery".equals(statusBarNotification.getTag()))
        {
            //HACK: Android low battery notification are posted again every few seconds. Ignore them, as we already have a battery indicator.
            return;
        }

        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_NOTIFICATION);

        if (packageName.equals("org.kde.kdeconnect_tp"))
        {
            //Make our own notifications silent :)
            np.set("silent", true);
            np.set("requestAnswer", true); //For compatibility with old desktop versions of KDE Connect that don't support "silent"
        }
/*
        if (sendIcons) {
            try {
                Drawable drawableAppIcon = AppsHelper.appIconLookup(context, packageName);
                Bitmap appIcon = ImagesHelper.drawableToBitmap(drawableAppIcon);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                if (appIcon.getWidth() > 128) {
                    appIcon = Bitmap.createScaledBitmap(appIcon, 96, 96, true);
                }
                appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
                byte[] bitmapData = outStream.toByteArray();
                np.setPayload(bitmapData);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("NotificationsPlugin", "Error retrieving icon");
            }
        }
*/
        np.set("id", key);
        np.set("appName", appName == null? packageName : appName);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("ticker", getTickerText(notification));
        np.set("time", Long.toString(statusBarNotification.getPostTime()));
        if (requestAnswer) {
            np.set("requestAnswer", true);
            np.set("silent", true);
        }

        device.sendPackage(np);
    }


    /**
     * Returns the ticker text of the notification.
     * If device android version is KitKat or newer, the title and text of the notification is used
     * instead the ticker text.
     */
    private String getTickerText(Notification notification) {
        final String TITLE_KEY = "android.title";
        final String TEXT_KEY = "android.text";
        String ticker = "";

        if(notification != null) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    Bundle extras = notification.extras;
                    String extraTitle = extras.getString(TITLE_KEY);
                    String extraText = null;
                    Object extraTextExtra = extras.get(TEXT_KEY);
                    if (extraTextExtra != null) extraText = extraTextExtra.toString();

                    if (extraTitle != null && extraText != null && !extraText.isEmpty()) {
                        ticker = extraTitle + " ‐ " + extraText;
                    } else if (extraTitle != null) {
                        ticker = extraTitle;
                    } else if (extraText != null) {
                        ticker = extraText;
                    }
                } catch(Exception e) {
                    Log.w("NotificationPlugin","problem parsing notification extras for " + notification.tickerText);
                    e.printStackTrace();
                }
            }

            if (ticker.isEmpty()) {
                ticker =  (notification.tickerText != null)? notification.tickerText.toString() : "";
            }
        }

        return ticker;
    }


    @Override
    public boolean onPackageReceived(final NetworkPackage np) {
/*
        if (np.getBoolean("sendIcons")) {
            sendIcons = true;
        }
*/
        if (np.getBoolean("request")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                    private void sendCurrentNotifications(NotificationReceiver service) {
                        StatusBarNotification[] notifications = service.getActiveNotifications();
                        for (StatusBarNotification notification : notifications) {
                            sendNotification(notification, true);
                        }
                    }


                    @Override
                    public void onServiceStart(final NotificationReceiver service) {
                        try {
                            //If service just started, this call will throw an exception because the answer is not ready yet
                            sendCurrentNotifications(service);
                        } catch(Exception e) {
                            Log.e("onPackageReceived","Error when answering 'request': Service failed to start. Retrying in 100ms...");
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(100);
                                        Log.e("onPackageReceived","Error when answering 'request': Service failed to start. Retrying...");
                                        sendCurrentNotifications(service);
                                    } catch (Exception e) {
                                        Log.e("onPackageReceived","Error when answering 'request': Service failed to start twice!");
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        }
                    }
                });

        } else if (np.has("cancel")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                    @Override
                    public void onServiceStart(NotificationReceiver service) {
                        String dismissedId = np.getString("cancel");
                        cancelNotificationCompat(service, dismissedId);
                    }
                });

        }

        return true;
    }


    @Override
    public AlertDialog getErrorDialog(final Activity deviceActivity) {

        if (Build.VERSION.SDK_INT < 18) {
            return new AlertDialog.Builder(deviceActivity)
                    .setTitle(R.string.pref_plugin_notifications)
                    .setMessage(R.string.plugin_not_available)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })
                    .create();
        } else {
            return new AlertDialog.Builder(deviceActivity)
                    .setTitle(R.string.pref_plugin_notifications)
                    .setMessage(R.string.no_permissions)
                    .setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                            deviceActivity.startActivityForResult(intent, MaterialActivity.RESULT_NEEDS_RELOAD);
                        }
                    })
                    .setNegativeButton(R.string.cancel,new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //Do nothing
                        }
                    })
                    .create();
        }
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION_REQUEST};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION};
    }

    //For compat with API<21, because lollipop changed the way to cancel notifications
    public static void cancelNotificationCompat(NotificationReceiver service, String compatKey) {
        if (Build.VERSION.SDK_INT >= 21) {
            service.cancelNotification(compatKey);
        } else {
            int first = compatKey.indexOf(':');
            if (first == -1) {
                Log.e("cancelNotificationCompa","Not formated like a notification key: "+ compatKey);
                return;
            }
            int last = compatKey.lastIndexOf(':');
            String packageName = compatKey.substring(0, first);
            String tag = compatKey.substring(first + 1, last);
            if (tag.length() == 0) tag = null;
            String idString = compatKey.substring(last + 1);
            int id;
            try {
                id = Integer.parseInt(idString);
            } catch (Exception e) {
                id = 0;
            }
            service.cancelNotification(packageName, tag, id);
        }
    }

    public static String getNotificationKeyCompat(StatusBarNotification statusBarNotification) {
        String result;
        // first check if it's one of our remoteIds
        String tag = statusBarNotification.getTag();
        if (tag != null && tag.startsWith("kdeconnectId:"))
            result = Integer.toString(statusBarNotification.getId());
        else if (Build.VERSION.SDK_INT >= 21) {
            result = statusBarNotification.getKey();
        } else {
            String packageName = statusBarNotification.getPackageName();
            int id = statusBarNotification.getId();
            String safePackageName = (packageName == null) ? "" : packageName;
            String safeTag = (tag == null) ? "" : tag;
            result = safePackageName + ":" + safeTag + ":" + id;
        }
        return result;
    }
}
