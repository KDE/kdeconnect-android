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
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.DialogFragment;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
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
import java.util.Objects;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
@PluginFactory.LoadablePlugin
public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";
    private final static String PACKET_TYPE_NOTIFICATION_REPLY = "kdeconnect.notification.reply";
    private final static String PACKET_TYPE_NOTIFICATION_ACTION = "kdeconnect.notification.action";

    private final static String TAG = "KDE/NotificationsPlugin";

    private AppDatabase appDatabase;

    private Set<String> currentNotifications;
    private Map<String, RepliableNotification> pendingIntents;
    private MultiValuedMap<String, Notification.Action> actions;
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
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        if (hasPermission()) {
            Intent intent = new Intent(activity, NotificationFilterActivity.class);
            activity.startActivity(intent);
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
        return StringUtils.contains(notificationListenerList, context.getPackageName());
    }

    @Override
    public boolean onCreate() {

        if (!hasPermission()) return false;

        pendingIntents = new HashMap<>();
        currentNotifications = new HashSet<>();
        actions = new ArrayListValuedHashMap<>();

        appDatabase = new AppDatabase(context, true);

        NotificationReceiver.RunCommand(context, service -> {

            service.addListener(NotificationsPlugin.this);

            serviceReady = service.isConnected();
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
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            Log.w(TAG, "onNotificationRemoved: notification is null");
            return;
        }
        String id = getNotificationKeyCompat(statusBarNotification);

        actions.remove(id);

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
                || (notification.flags & NotificationCompat.FLAG_GROUP_SUMMARY) != 0 //The notification that groups other notifications
        )
        {
            //This is not a notification we want!
            return;
        }

        if (!appDatabase.isEnabled(statusBarNotification.getPackageName())) {
            return;
            // we don't want notification from this app
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
        //If it's an update, the other end should have the icon already: no need to extract it and create the payload again
        if (!isUpdate) {

            currentNotifications.add(key);

            Bitmap appIcon = extractIcon(statusBarNotification, notification);

            if (appIcon != null && !appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)) {
                attachIcon(np, appIcon);
            }
        }

        np.set("actions", extractActions(notification, key));

        np.set("id", key);
        np.set("onlyOnce", (notification.flags & NotificationCompat.FLAG_ONLY_ALERT_ONCE) != 0);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("appName", StringUtils.defaultString(appName, packageName));
        np.set("time", Long.toString(statusBarNotification.getPostTime()));

        if (!appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS)) {
            RepliableNotification rn = extractRepliableNotification(statusBarNotification);
            if (rn != null) {
                np.set("requestReplyId", rn.id);
                pendingIntents.put(rn.id, rn);
            }
            np.set("ticker", getTickerText(notification));

            Pair<String, String> conversation = extractConversation(notification);

            if (conversation.first != null) {
                np.set("title", conversation.first);
            } else {
                np.set("title", extractStringFromExtra(getExtras(notification), NotificationCompat.EXTRA_TITLE));
            }

            np.set("text", extractText(notification, conversation));
        }

        device.sendPacket(np);
    }

    private String extractText(Notification notification, Pair<String, String> conversation) {

        if (conversation.second != null) {
            return conversation.second;
        }

        Bundle extras = getExtras(notification);

        if (extras.containsKey(NotificationCompat.EXTRA_BIG_TEXT)) {
            return extractStringFromExtra(extras, NotificationCompat.EXTRA_BIG_TEXT);
        }

        return extractStringFromExtra(extras, NotificationCompat.EXTRA_TEXT);
    }

    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private static Bundle getExtras(Notification notification) {
        // NotificationCompat.getExtras() is expected to return non-null values for JELLY_BEAN+
        return Objects.requireNonNull(NotificationCompat.getExtras(notification));
    }

    private void attachIcon(NetworkPacket np, Bitmap appIcon) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
        byte[] bitmapData = outStream.toByteArray();

        np.setPayload(new NetworkPacket.Payload(bitmapData));
        np.set("payloadHash", getChecksum(bitmapData));
    }

    @Nullable
    private Bitmap extractIcon(StatusBarNotification statusBarNotification, Notification notification) {
        try {
            Context foreignContext = context.createPackageContext(statusBarNotification.getPackageName(), 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notification.getLargeIcon() != null) {
                return iconToBitmap(foreignContext, notification.getLargeIcon());
            } else if (notification.largeIcon != null) {
                return notification.largeIcon;
            }

            PackageManager pm = context.getPackageManager();
            Resources foreignResources = pm.getResourcesForApplication(statusBarNotification.getPackageName());
            Drawable foreignIcon = foreignResources.getDrawable(notification.icon); //Might throw Resources.NotFoundException
            return drawableToBitmap(foreignIcon);

        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            Log.e(TAG, "Package not found", e);
        }

        return null;
    }

    @Nullable
    private JSONArray extractActions(Notification notification, String key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || ArrayUtils.isEmpty(notification.actions)) {
            return null;
        }

        JSONArray jsonArray = new JSONArray();

        for (Notification.Action action : notification.actions) {

            if (null == action.title)
                continue;

            // Check whether it is a reply action. We have special treatment for them
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH &&
                    ArrayUtils.isNotEmpty(action.getRemoteInputs()))
                continue;

            jsonArray.put(action.title.toString());

            // A list is automatically created if it doesn't already exist.
            actions.put(key, action);
        }

        return jsonArray;
    }

    private Pair<String, String> extractConversation(Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return new Pair<>(null, null);

        if (!notification.extras.containsKey(Notification.EXTRA_MESSAGES))
            return new Pair<>(null, null);

        Parcelable[] ms = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);

        if (ms == null)
            return new Pair<>(null, null);

        String title = notification.extras.getString(Notification.EXTRA_CONVERSATION_TITLE);

        boolean isGroupConversation = notification.extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION);

        StringBuilder messagesBuilder = new StringBuilder();

        for (Parcelable p : ms) {
            Bundle m = (Bundle) p;

            if (isGroupConversation && m.containsKey("sender")) {
                messagesBuilder.append(m.get("sender"));
                messagesBuilder.append(": ");
            }

            messagesBuilder.append(extractStringFromExtra(m, "text"));
            messagesBuilder.append("\n");
        }

        return new Pair<>(title, messagesBuilder.toString());
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
            Log.e(TAG, "No such notification");
            return;
        }

        RepliableNotification repliableNotification = pendingIntents.get(id);
        if (repliableNotification == null) {
            Log.e(TAG, "No such notification");
            return;
        }
        RemoteInput[] remoteInputs = new RemoteInput[repliableNotification.remoteInputs.size()];

        Intent localIntent = new Intent();
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle localBundle = new Bundle();
        int i = 0;
        for (RemoteInput remoteIn : repliableNotification.remoteInputs) {
            remoteInputs[i] = remoteIn;
            localBundle.putCharSequence(remoteInputs[i].getResultKey(), message);
            i++;
        }
        RemoteInput.addResultsToIntent(remoteInputs, localIntent, localBundle);

        try {
            repliableNotification.pendingIntent.send(context, 0, localIntent);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "replyToNotification error: " + e.getMessage());
        }
        pendingIntents.remove(id);
    }

    @Nullable
    private RepliableNotification extractRepliableNotification(StatusBarNotification statusBarNotification) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }

        if (statusBarNotification.getNotification().actions == null) {
            return null;
        }

        for (Notification.Action act : statusBarNotification.getNotification().actions) {
            if (act != null && act.getRemoteInputs() != null) {
                // Is a reply
                RepliableNotification repliableNotification = new RepliableNotification();
                repliableNotification.remoteInputs.addAll(Arrays.asList(act.getRemoteInputs()));
                repliableNotification.pendingIntent = act.actionIntent;
                repliableNotification.packageName = statusBarNotification.getPackageName();
                repliableNotification.tag = statusBarNotification.getTag(); //TODO find how to pass Tag with sending PendingIntent, might fix Hangout problem

                return repliableNotification;
            }
        }

        return null;
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
            Log.e(TAG, "Don't know how to extract text from extra of type: " + extra.getClass().getCanonicalName());
            return null;
        }
    }

    /**
     * Returns the ticker text of the notification.
     * If device android version is KitKat or newer, the title and text of the notification is used
     * instead the ticker text.
     */
    private String getTickerText(Notification notification) {
        String ticker = "";

        try {
            Bundle extras = getExtras(notification);
            String extraTitle = extractStringFromExtra(extras, NotificationCompat.EXTRA_TITLE);
            String extraText = extractStringFromExtra(extras, NotificationCompat.EXTRA_TEXT);

            if (extraTitle != null && !TextUtils.isEmpty(extraText)) {
                ticker = extraTitle + ": " + extraText;
            } else if (extraTitle != null) {
                ticker = extraTitle;
            } else if (extraText != null) {
                ticker = extraText;
            }
        } catch (Exception e) {
            Log.e(TAG, "problem parsing notification extras for " + notification.tickerText, e);
        }

        if (ticker.isEmpty()) {
            ticker = (notification.tickerText != null) ? notification.tickerText.toString() : "";
        }

        return ticker;
    }

    private void sendCurrentNotifications(NotificationReceiver service) {
        StatusBarNotification[] notifications = service.getActiveNotifications();
        if (notifications != null) { //Can happen only on API 23 and lower
            for (StatusBarNotification notification : notifications) {
                sendNotification(notification);
            }
        }
    }

    @Override
    public boolean onPacketReceived(final NetworkPacket np) {

        if (np.getType().equals(PACKET_TYPE_NOTIFICATION_ACTION) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            String key = np.getString("key");
            String title = np.getString("action");
            PendingIntent intent = null;

            for (Notification.Action a : actions.get(key)) {
                if (a.title.equals(title)) {
                    intent = a.actionIntent;
                }
            }

            if (intent != null) {
                try {
                    intent.send();
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Triggering action failed", e);
                }
            }

        } else if (np.getBoolean("request")) {

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
    public DialogFragment getPermissionExplanationDialog() {
        return new StartActivityAlertDialogFragment.Builder()
                .setTitle(R.string.pref_plugin_notifications)
                .setMessage(R.string.no_permissions)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .setStartForResult(true)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create();
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST, PACKET_TYPE_NOTIFICATION_REPLY, PACKET_TYPE_NOTIFICATION_ACTION};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    //For compat with API<21, because lollipop changed the way to cancel notifications
    private static void cancelNotificationCompat(NotificationReceiver service, String compatKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            service.cancelNotification(compatKey);
        } else {
            int first = compatKey.indexOf(':');
            if (first == -1) {
                Log.e(TAG, "Not formatted like a notification key: " + compatKey);
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
        if (StringUtils.startsWith(tag, "kdeconnectId:"))
            result = Integer.toString(statusBarNotification.getId());
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result = statusBarNotification.getKey();
        } else {
            String packageName = statusBarNotification.getPackageName();
            int id = statusBarNotification.getId();
            result = StringUtils.defaultString(packageName) + ":" + StringUtils.defaultString(tag) +
                    ":" + id;
        }
        return result;
    }

    private String getChecksum(byte[] data) {

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error while generating checksum", e);
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
