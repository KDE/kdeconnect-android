/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.notifications;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.core.app.NotificationCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.kde.kdeconnect.helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.plugins.Plugin;
import org.kde.kdeconnect.plugins.PluginFactory;
import org.kde.kdeconnect.ui.MainActivity;
import org.kde.kdeconnect.ui.PluginSettingsFragment;
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment;
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

@PluginFactory.LoadablePlugin
public class NotificationsPlugin extends Plugin implements NotificationReceiver.NotificationListener {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";
    private final static String PACKET_TYPE_NOTIFICATION_REPLY = "kdeconnect.notification.reply";
    private final static String PACKET_TYPE_NOTIFICATION_ACTION = "kdeconnect.notification.action";
    private final static String PREF_KEY = "prefKey";
    protected static final int PREF_NOTIFICATION_SCREEN_OFF = R.string.screen_off_notification_state;

    private final static String TAG = "KDE/NotificationsPlugin";

    private AppDatabase appDatabase;

    private Set<String> currentNotifications;
    private Map<String, String> notificationsIcons; // Here we will map every notification to it's icon(hash)
    private Map<String, RepliableNotification> pendingIntents;
    private MultiValuedMap<String, Notification.Action> actions;
    private boolean serviceReady;
    private SharedPreferences sharedPreferences;
    private KeyguardManager keyguardManager;

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_notifications);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_notifications_desc);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        Intent intent = new Intent(activity, NotificationFilterActivity.class);
        intent.putExtra(PREF_KEY, this.getSharedPreferencesName());
        activity.startActivity(intent);
        return null;
    }

    @Override
    public boolean checkRequiredPermissions() {
        return hasNotificationsPermission();
    }

    private boolean hasNotificationsPermission() {
        //Notifications use a different kind of permission, because it was added before the current runtime permissions model
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return notificationListenerList != null && notificationListenerList.contains(context.getPackageName());
    }

    @Override
    public boolean onCreate() {

        pendingIntents = new HashMap<>();
        currentNotifications = new HashSet<>();
        notificationsIcons = new HashMap<>();
        actions = new ArrayListValuedHashMap<>();

        sharedPreferences = context.getSharedPreferences(getSharedPreferencesName(),Context.MODE_PRIVATE);

        keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        appDatabase = AppDatabase.getInstance(context);

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

        if (!appDatabase.isEnabled(statusBarNotification.getPackageName())) {
            currentNotifications.remove(id);
            return;
        }

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION);
        np.set("id", id);
        np.set("isCancel", true);
        getDevice().sendPacket(np);
        currentNotifications.remove(id);
        notificationsIcons.remove(id);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (sharedPreferences != null && sharedPreferences.getBoolean(context.getString(PREF_NOTIFICATION_SCREEN_OFF),false)){
            if (keyguardManager != null && keyguardManager.inKeyguardRestrictedInputMode()){
                sendNotification(statusBarNotification, false);
            }
        }else {
            sendNotification(statusBarNotification, false);
        }
    }

    // isPreexisting is true for notifications that we are sending in response to a request command
    // and that we want to send with the "silent" flag set
    private void sendNotification(StatusBarNotification statusBarNotification, boolean isPreexisting) {

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

        if ("com.android.systemui".equals(packageName)) {
            if("low_battery".equals(statusBarNotification.getTag())) {
                //HACK: Android low battery notification are posted again every few seconds. Ignore them, as we already have a battery indicator.
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if ("MediaOngoingActivity".equals(notification.getChannelId())) {
                    //HACK: Samsung OneUI sends this notification when media playback is started. Ignore it, as this is handled by Mpris plugin
                    return;
                }
            }
        }

        if ("org.kde.kdeconnect_tp".equals(packageName) || "org.kde.kdeconnect_tp.debug".equals(packageName)) {
            // Don't send our own notifications
            return;
        }

        boolean isUpdate = currentNotifications.contains(key);

        if (!isUpdate) {
            currentNotifications.add(key);
        }

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION);

        Bitmap appIcon = extractIcon(statusBarNotification, notification);

        if (appIcon != null && !appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)) {
            byte[] iconBytes = getIconBytes(appIcon);
            String iconHash = getIconHash(iconBytes);

            //If it's the same icon, the other end should have it already, so there's no need to send it again.
            if (!iconHash.equals(notificationsIcons.get(key))) {
                attachIcon(np, iconBytes);
                notificationsIcons.put(key, iconHash);
            }
            //We should always send the icon's hash so the other end can know which icon to use.
            np.set("payloadHash", iconHash);
        }


        np.set("actions", extractActions(notification, key));

        np.set("id", key);
        np.set("isClearable", statusBarNotification.isClearable());
        np.set("appName", StringUtils.defaultString(appName, packageName));
        np.set("time", Long.toString(statusBarNotification.getPostTime()));
        np.set("silent", isPreexisting);

        if (!appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS)) {
            RepliableNotification rn = extractRepliableNotification(statusBarNotification);
            if (rn != null) {
                np.set("requestReplyId", rn.id);
                pendingIntents.put(rn.id, rn);
            }
            np.set("ticker", getTickerText(notification));

            Pair<String, String> conversation = extractConversation(notification);

            String title = conversation.first;
            if (title == null) {
                title = extractStringFromExtra(getExtras(notification), NotificationCompat.EXTRA_TITLE);
            }
            if (title != null) {
                np.set("title", title);
            }

            String text = extractText(notification, conversation);
            if (text != null) {
                np.set("text", text);
            }
        }

        getDevice().sendPacket(np);
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
    private static Bundle getExtras(Notification notification) {
        // NotificationCompat.getExtras() is expected to return non-null values for JELLY_BEAN+
        return Objects.requireNonNull(NotificationCompat.getExtras(notification));
    }

    private byte[] getIconBytes(Bitmap appIcon) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream);
        return outStream.toByteArray();
    }

    private String getIconHash(byte[] iconBytes) {
        return getChecksum(iconBytes);
    }

    private void attachIcon(NetworkPacket np, byte[] iconBytes) {
        np.setPayload(new NetworkPacket.Payload(iconBytes));
    }

    @Nullable
    private Bitmap extractIcon(StatusBarNotification statusBarNotification, Notification notification) {
        try {
            Context foreignContext = context.createPackageContext(statusBarNotification.getPackageName(), 0);

            if (notification.getLargeIcon() != null) {
                return iconToBitmap(foreignContext, notification.getLargeIcon());
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
        if (ArrayUtils.isEmpty(notification.actions)) {
            return null;
        }

        JSONArray jsonArray = new JSONArray();

        for (Notification.Action action : notification.actions) {

            if (null == action.title)
                continue;

            // Check whether it is a reply action. We have special treatment for them
            if (ArrayUtils.isNotEmpty(action.getRemoteInputs()))
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

        Parcelable[] ms = BundleCompat.getParcelableArray(notification.extras, Notification.EXTRA_MESSAGES, Parcelable.class);

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

    private Bitmap iconToBitmap(Context foreignContext, Icon icon) {
        if (icon == null) return null;

        return drawableToBitmap(icon.loadDrawable(foreignContext));
    }

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
     * Returns the the title and text of the notification.
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
        if (!hasNotificationsPermission()) {
            return;
        }
        StatusBarNotification[] notifications;
        try {
            notifications = service.getActiveNotifications();
        } catch (SecurityException e) {
            return;
        }
        if (notifications == null) {
            return; //Can happen only on API 23 and lower
        }
        for (StatusBarNotification notification : notifications) {
            sendNotification(notification, true);
        }
    }

    @Override
    public boolean onPacketReceived(final NetworkPacket np) {

        if (np.getType().equals(PACKET_TYPE_NOTIFICATION_ACTION)) {

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
            NotificationReceiver.RunCommand(context, service -> service.cancelNotification(dismissedId));
        } else if (np.has("requestReplyId") && np.has("message")) {
            replyToNotification(np.getString("requestReplyId"), np.getString("message"));
        }

        return true;
    }

    @Override
    public @NonNull DialogFragment getPermissionExplanationDialog() {
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
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST, PACKET_TYPE_NOTIFICATION_REPLY, PACKET_TYPE_NOTIFICATION_ACTION};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    private static String getNotificationKeyCompat(StatusBarNotification statusBarNotification) {
        String result;
        // first check if it's one of our remoteIds
        String tag = statusBarNotification.getTag();
        if (tag != null && tag.startsWith("kdeconnectId:"))
            result = Integer.toString(statusBarNotification.getId());
        else {
            result = statusBarNotification.getKey();
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

    public static String getPrefKey(){ return PREF_KEY;}
}
