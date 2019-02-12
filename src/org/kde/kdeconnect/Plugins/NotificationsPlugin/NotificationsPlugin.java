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
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.util.Log;

import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.AlertDialogFragment;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.R;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
@PluginFactory.LoadablePlugin
public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";
    private final static String PACKET_TYPE_NOTIFICATION_REPLY = "kdeconnect.notification.reply";

    private AppDatabase appDatabase;

    private Set<String> currentNotifications;
    private Map<String, RepliableNotification> pendingIntents;
    private boolean serviceReady;

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
    public PluginSettingsFragment getSettingsFragment() {
        if (hasPermission()) {
            Context context = device.getContext();
            Intent intent = new Intent(context, NotificationFilterActivity.class);
            context.startActivity(intent);
        }

        return null;
    }

    @Override
    public boolean checkRequiredPermissions() {
        //Notifications use a different kind of permission, because it was added before the current runtime permissions model
        return hasPermission();
    }

    private boolean hasPermission() {
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return (notificationListenerList != null && notificationListenerList.contains(context.getPackageName()));
    }

    @Override
    public boolean onCreate() {

        if (!hasPermission()) return false;

        pendingIntents = new HashMap<>();
        currentNotifications = new HashSet<>();

        appDatabase = new AppDatabase(context, true);

        NotificationReceiver.RunCommand(context, service -> {

            service.addListener(NotificationsPlugin.this);

            serviceReady = service.isConnected();

            if (serviceReady) {
                sendCurrentNotifications(service);
            }
        });

        return true;
    }

    @Override
    public void onDestroy() {

        NotificationReceiver.RunCommand(context, service -> service.removeListener(NotificationsPlugin.this));
    }

    @Override
    public void onListenerConnected(NotificationReceiver service) {
        serviceReady = true;
        sendCurrentNotifications(service);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            Log.w("onNotificationRemoved", "notification is null");
            return;
        }
        String id = getNotificationKeyCompat(statusBarNotification);
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION);
        np.set("id", id);
        np.set("isCancel", true);
        device.sendPacket(np);
        currentNotifications.remove(id);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        sendNotification(statusBarNotification);
    }

    private void sendNotification(StatusBarNotification statusBarNotification) {

        Notification notification = statusBarNotification.getNotification();

        if ((notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0
                || (notification.flags & NotificationCompat.FLAG_GROUP_SUMMARY) != 0) //The notification that groups other notifications
        {
            //This is not a notification we want!
            return;
        }

        if (!appDatabase.isEnabled(statusBarNotification.getPackageName())) {
            return;
            // we dont want notification from this app
        }

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

        if ("org.kde.kdeconnect_tp".equals(packageName)) {
            // Don't send our own notifications
            return;
        }

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION);

        boolean isUpdate = currentNotifications.contains(key);
        if (!isUpdate) {
            //If it's an update, the other end should have the icon already: no need to extract it and create the payload again
            try {
                Bitmap appIcon;
                Context foreignContext = context.createPackageContext(statusBarNotification.getPackageName(), 0);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    appIcon = iconToBitmap(foreignContext, notification.getLargeIcon());
                } else {
                    appIcon = notification.largeIcon;
                }

                if (appIcon == null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        appIcon = iconToBitmap(foreignContext, notification.getSmallIcon());
                    } else {
                        PackageManager pm = context.getPackageManager();
                        Resources foreignResources = pm.getResourcesForApplication(statusBarNotification.getPackageName());
                        Drawable foreignIcon = foreignResources.getDrawable(notification.icon);
                        appIcon = drawableToBitmap(foreignIcon);
                    }
                }

                if (appIcon != null && !appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)) {

                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
                    byte[] bitmapData = outStream.toByteArray();

                    Log.e("PAYLOAD", "PAYLOAD: " + getChecksum(bitmapData));

                    np.setPayload(new NetworkPacket.Payload(bitmapData));

                    np.set("payloadHash", getChecksum(bitmapData));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("NotificationsPlugin", "Error retrieving icon");
            }
        } else {
            currentNotifications.add(key);
        }

        np.set("id", key);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("appName", appName == null ? packageName : appName);
        np.set("time", Long.toString(statusBarNotification.getPostTime()));
        if (!appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS)) {
            RepliableNotification rn = extractRepliableNotification(statusBarNotification);
            if (rn.pendingIntent != null) {
                np.set("requestReplyId", rn.id);
                pendingIntents.put(rn.id, rn);
            }
            np.set("ticker", getTickerText(notification));
            np.set("title", getNotificationTitle(notification));
            np.set("text", getNotificationText(notification));
        }

        device.sendPacket(np);
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;

        Bitmap res;
        if (drawable.getIntrinsicWidth() > 128 || drawable.getIntrinsicHeight() > 128) {
            res = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        } else if (drawable.getIntrinsicWidth() <= 64 || drawable.getIntrinsicHeight() <= 64) {
            res = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        } else {
            res = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(res);
        drawable.setBounds(0, 0, res.getWidth(), res.getHeight());
        drawable.draw(canvas);
        return res;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private Bitmap iconToBitmap(Context foreignContext, Icon icon) {
        if (icon == null) return null;

        return drawableToBitmap(icon.loadDrawable(foreignContext));
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
                    title = extractStringFromExtra(extras, TITLE_KEY);
                } catch (Exception e) {
                    Log.w("NotificationPlugin", "problem parsing notification extras for " + notification.tickerText);
                    e.printStackTrace();
                }
            }
        }

        return title;
    }

    private RepliableNotification extractRepliableNotification(StatusBarNotification statusBarNotification) {
        RepliableNotification repliableNotification = new RepliableNotification();

        if (statusBarNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    if (statusBarNotification.getNotification().actions != null) {
                        for (Notification.Action act : statusBarNotification.getNotification().actions) {
                            if (act != null && act.getRemoteInputs() != null) {
                                // Is a reply
                                repliableNotification.remoteInputs.addAll(Arrays.asList(act.getRemoteInputs()));
                                repliableNotification.pendingIntent = act.actionIntent;
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

        return text;
    }


    private static String extractStringFromExtra(Bundle extras, String key) {
        Object extra = extras.get(key);
        if (extra == null) {
            return null;
        } else if (extra instanceof String) {
            return (String) extra;
        } else if (extra instanceof SpannableString) {
            return extra.toString();
        } else {
            Log.e("NotificationsPlugin", "Don't know how to extract text from extra of type: " + extra.getClass().getCanonicalName());
            return null;
        }
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
                    String extraTitle = extractStringFromExtra(extras, TITLE_KEY);
                    String extraText = extractStringFromExtra(extras, TEXT_KEY);

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

    private void sendCurrentNotifications(NotificationReceiver service) {
        StatusBarNotification[] notifications = service.getActiveNotifications();
        for (StatusBarNotification notification : notifications) {
            sendNotification(notification);
        }
    }

    @Override
    public boolean onPacketReceived(final NetworkPacket np) {

        if (np.getBoolean("request")) {

            if (serviceReady) {
                NotificationReceiver.RunCommand(context, this::sendCurrentNotifications);
            }

        } else if (np.has("cancel")) {
            final String dismissedId = np.getString("cancel");
            currentNotifications.remove(dismissedId);
            NotificationReceiver.RunCommand(context, service -> cancelNotificationCompat(service, dismissedId));
        } else if (np.has("requestReplyId") && np.has("message")) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                replyToNotification(np.getString("requestReplyId"), np.getString("message"));
            }

        }

        return true;
    }

    @Override
    public AlertDialogFragment getPermissionExplanationDialog(int requestCode) {
        return new StartActivityAlertDialogFragment.Builder()
                .setTitle(R.string.pref_plugin_notifications)
                .setMessage(R.string.no_permissions)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .setStartForResult(true)
                .setRequestCode(requestCode)
                .create();
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST, PACKET_TYPE_NOTIFICATION_REPLY};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    //For compat with API<21, because lollipop changed the way to cancel notifications
    private static void cancelNotificationCompat(NotificationReceiver service, String compatKey) {
        if (Build.VERSION.SDK_INT >= 21) {
            service.cancelNotification(compatKey);
        } else {
            int first = compatKey.indexOf(':');
            if (first == -1) {
                Log.e("cancelNotificationCompa", "Not formatted like a notification key: " + compatKey);
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
