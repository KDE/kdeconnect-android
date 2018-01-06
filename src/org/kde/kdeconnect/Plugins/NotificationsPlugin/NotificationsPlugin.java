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
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MaterialActivity;
import org.kde.kdeconnect.UserInterface.SettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    private final static String PACKAGE_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKAGE_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";
    private final static String PACKAGE_TYPE_NOTIFICATION_REPLY = "kdeconnect.notification.reply";

    private Map<String, RepliableNotification> pendingIntents;

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
        pendingIntents = new HashMap<>();

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
        return true;
    }

    @Override
    public void onDestroy() {

        NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
            @Override
            public void onServiceStart(NotificationReceiver service) {
                service.removeListener(NotificationsPlugin.this);
            }
        });
    }


    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            Log.w("onNotificationRemoved", "notification is null");
            return;
        }
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

    private void sendNotification(StatusBarNotification statusBarNotification, boolean requestAnswer) {

        Notification notification = statusBarNotification.getNotification();
        AppDatabase appDatabase = new AppDatabase(context);

        if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0) {
            //This is not a notification we want!
            return;
        }

        appDatabase.open();
        if (!appDatabase.isEnabled(statusBarNotification.getPackageName())) {
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
                "low_battery".equals(statusBarNotification.getTag())) {
            //HACK: Android low battery notification are posted again every few seconds. Ignore them, as we already have a battery indicator.
            return;
        }

        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_NOTIFICATION);

        if (packageName.equals("org.kde.kdeconnect_tp")) {
            //Make our own notifications silent :)
            np.set("silent", true);
            np.set("requestAnswer", true); //For compatibility with old desktop versions of KDE Connect that don't support "silent"
        }

        try {
            Bitmap appIcon = notification.largeIcon;

            if (appIcon != null) {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                if (appIcon.getWidth() > 128) {
                    appIcon = Bitmap.createScaledBitmap(appIcon, 96, 96, true);
                }
                appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
                byte[] bitmapData = outStream.toByteArray();

                np.setPayload(bitmapData);

                np.set("payloadHash", getChecksum(bitmapData));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NotificationsPlugin", "Error retrieving icon");
        }

        RepliableNotification rn = extractRepliableNotification(statusBarNotification);
        if (rn.pendingIntent != null) {
            np.set("requestReplyId", rn.id);
            pendingIntents.put(rn.id, rn);
        }

        np.set("id", key);
        np.set("appName", appName == null ? packageName : appName);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("ticker", getTickerText(notification));
        np.set("title", getNotificationTitle(notification));
        np.set("text", getNotificationText(notification));
        np.set("time", Long.toString(statusBarNotification.getPostTime()));
        if (requestAnswer) {
            np.set("requestAnswer", true);
            np.set("silent", true);
        }

        device.sendPackage(np);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
    private void replyToNotification(String id, String message) {
        if (pendingIntents.isEmpty() || !pendingIntents.containsKey(id)) {
            Log.e("NotificationsPlugin", "No such notification");
            return;
        }

        RepliableNotification repliableNotification = pendingIntents.get(id);
        if (repliableNotification == null) {
            Log.e("NotificationsPlugin", "No such notification");
            return;
        }
        RemoteInput[] remoteInputs = new RemoteInput[repliableNotification.remoteInputs.size()];

        Intent localIntent = new Intent();
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle localBundle = new Bundle();
        int i = 0;
        for (RemoteInput remoteIn : repliableNotification.remoteInputs) {
            getDetailsOfNotification(remoteIn);
            remoteInputs[i] = remoteIn;
            localBundle.putCharSequence(remoteInputs[i].getResultKey(), message);
            i++;
        }
        RemoteInput.addResultsToIntent(remoteInputs, localIntent, localBundle);

        try {
            repliableNotification.pendingIntent.send(context, 0, localIntent);
        } catch (PendingIntent.CanceledException e) {
            Log.e("NotificationPlugin", "replyToNotification error: " + e.getMessage());
        }
        pendingIntents.remove(id);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
    private void getDetailsOfNotification(RemoteInput remoteInput) {
        //Some more details of RemoteInput... no idea what for but maybe it will be useful at some point
        String resultKey = remoteInput.getResultKey();
        String label = remoteInput.getLabel().toString();
        Boolean canFreeForm = remoteInput.getAllowFreeFormInput();
        if (remoteInput.getChoices() != null && remoteInput.getChoices().length > 0) {
            String[] possibleChoices = new String[remoteInput.getChoices().length];
            for (int i = 0; i < remoteInput.getChoices().length; i++) {
                possibleChoices[i] = remoteInput.getChoices()[i].toString();
            }
        }
    }

    private String getNotificationTitle(Notification notification) {
        final String TITLE_KEY = "android.title";
        final String TEXT_KEY = "android.text";
        String title = "";

        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    Bundle extras = notification.extras;
                    title = extras.getString(TITLE_KEY);
                } catch (Exception e) {
                    Log.w("NotificationPlugin", "problem parsing notification extras for " + notification.tickerText);
                    e.printStackTrace();
                }
            }
        }

        //TODO Add compat for under Kitkat devices

        return title;
    }

    private RepliableNotification extractRepliableNotification(StatusBarNotification statusBarNotification) {
        RepliableNotification repliableNotification = new RepliableNotification();

        if (statusBarNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Boolean reply = false;

                    //works for WhatsApp, but not for Telegram
                    if (statusBarNotification.getNotification().actions != null) {
                        for (Notification.Action act : statusBarNotification.getNotification().actions) {
                            if (act != null && act.getRemoteInputs() != null) {
                                repliableNotification.remoteInputs.addAll(Arrays.asList(act.getRemoteInputs()));
                                repliableNotification.pendingIntent = act.actionIntent;
                                reply = true;
                                break;
                            }
                        }

                        repliableNotification.packageName = statusBarNotification.getPackageName();

                        repliableNotification.tag = statusBarNotification.getTag();//TODO find how to pass Tag with sending PendingIntent, might fix Hangout problem
                    }
                } catch (Exception e) {
                    Log.w("NotificationPlugin", "problem extracting notification wear for " + statusBarNotification.getNotification().tickerText);
                    e.printStackTrace();
                }
            }
        }

        return repliableNotification;
    }

    private String getNotificationText(Notification notification) {
        final String TEXT_KEY = "android.text";
        String text = "";

        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    Bundle extras = notification.extras;
                    Object extraTextExtra = extras.get(TEXT_KEY);
                    if (extraTextExtra != null) text = extraTextExtra.toString();
                } catch (Exception e) {
                    Log.w("NotificationPlugin", "problem parsing notification extras for " + notification.tickerText);
                    e.printStackTrace();
                }
            }
        }

        //TODO Add compat for under Kitkat devices

        return text;
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

        if (notification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    Bundle extras = notification.extras;
                    String extraTitle = extras.getString(TITLE_KEY);
                    String extraText = null;
                    Object extraTextExtra = extras.get(TEXT_KEY);
                    if (extraTextExtra != null) extraText = extraTextExtra.toString();

                    if (extraTitle != null && extraText != null && !extraText.isEmpty()) {
                        ticker = extraTitle + ": " + extraText;
                    } else if (extraTitle != null) {
                        ticker = extraTitle;
                    } else if (extraText != null) {
                        ticker = extraText;
                    }
                } catch (Exception e) {
                    Log.w("NotificationPlugin", "problem parsing notification extras for " + notification.tickerText);
                    e.printStackTrace();
                }
            }

            if (ticker.isEmpty()) {
                ticker = (notification.tickerText != null) ? notification.tickerText.toString() : "";
            }
        }

        return ticker;
    }


    @Override
    public boolean onPackageReceived(final NetworkPackage np) {

        if (np.getBoolean("request")) {

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
                    } catch (Exception e) {
                        Log.e("onPackageReceived", "Error when answering 'request': Service failed to start. Retrying in 100ms...");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(100);
                                    Log.e("onPackageReceived", "Error when answering 'request': Service failed to start. Retrying...");
                                    sendCurrentNotifications(service);
                                } catch (Exception e) {
                                    Log.e("onPackageReceived", "Error when answering 'request': Service failed to start twice!");
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                }
            });

        } else if (np.has("cancel")) {

            NotificationReceiver.RunCommand(context, new NotificationReceiver.InstanceCallback() {
                @Override
                public void onServiceStart(NotificationReceiver service) {
                    String dismissedId = np.getString("cancel");
                    cancelNotificationCompat(service, dismissedId);
                }
            });

        } else if (np.has("requestReplyId") && np.has("message")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                replyToNotification(np.getString("requestReplyId"), np.getString("message"));
            }

        }

        return true;
    }


    @Override
    public AlertDialog getErrorDialog(final Activity deviceActivity) {

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
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Do nothing
                    }
                })
                .create();

    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION_REQUEST, PACKAGE_TYPE_NOTIFICATION_REPLY};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION};
    }

    //For compat with API<21, because lollipop changed the way to cancel notifications
    private static void cancelNotificationCompat(NotificationReceiver service, String compatKey) {
        if (Build.VERSION.SDK_INT >= 21) {
            service.cancelNotification(compatKey);
        } else {
            int first = compatKey.indexOf(':');
            if (first == -1) {
                Log.e("cancelNotificationCompa", "Not formated like a notification key: " + compatKey);
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

    private static String getNotificationKeyCompat(StatusBarNotification statusBarNotification) {
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

    private String getChecksum(byte[] data) {

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            Log.e("KDEConnect", "Error while generating checksum", e);
        }
        return null;
    }


    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.JELLY_BEAN_MR2;
    }
}
