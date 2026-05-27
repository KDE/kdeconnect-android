/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.notifications

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.fragment.app.DialogFragment
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.AppsHelper.appNameLookup
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale
import androidx.core.graphics.createBitmap

@LoadablePlugin
class NotificationsPlugin : Plugin(), NotificationReceiver.NotificationListener {
    private lateinit var appDatabase: AppDatabase
    private val currentNotifications = mutableSetOf<String>()
    // Here we will map every notification to it's icon(hash)
    private val notificationsIcons = mutableMapOf<String, String>()
    private val postedNotifications = mutableSetOf<String>()
    private val pendingIntents = mutableMapOf<String, RepliableNotification>()
    private val pendingActions = ArrayListValuedHashMap<String, Notification.Action>()
    private var serviceReady = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var mainHandler: Handler
    private val postedNotificationsLock = Any()

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_notifications)

    override val description: String
        get() = context.getString(R.string.pref_plugin_notifications_desc)

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment? {
        val intent = Intent(activity, NotificationFilterActivity::class.java)
        intent.putExtra(PREFERENCE_KEY, this.sharedPreferencesName)
        activity.startActivity(intent)
        return null
    }

    override fun checkRequiredPermissions(): Boolean {
        return NotificationReceiver.hasReadNotificationsPermission(context)
    }

    override fun onCreate(): Boolean {
        appDatabase = AppDatabase.getInstance(context)
        sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        mainHandler = Handler(Looper.getMainLooper())
        NotificationReceiver.RunCommand(context) { service ->
            service.addListener(this@NotificationsPlugin)
            serviceReady = service.isConnected
        }
        return true
    }

    override fun onDestroy() {
        currentNotifications.clear()
        notificationsIcons.clear()
        pendingIntents.clear()
        pendingActions.clear()

        synchronized(postedNotificationsLock) {
            mainHandler.removeCallbacksAndMessages(null)
            postedNotifications.clear()
        }

        NotificationReceiver.RunCommand(context) { service ->
            service.removeListener(this@NotificationsPlugin)
        }
    }

    override fun onListenerConnected(service: NotificationReceiver?) {
        serviceReady = true
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification?) {
        if (statusBarNotification == null) {
            Log.w(TAG, "onNotificationRemoved: notification is null")
            return
        }

        val id = getNotificationKeyCompat(statusBarNotification)

        synchronized(postedNotificationsLock) {
            postedNotifications.remove(id)
            cancelDelayedNotification(id)
        }

        pendingActions.remove(id)

        if (!appDatabase.isEnabled(statusBarNotification.packageName)) {
            currentNotifications.remove(id)
            return
        }

        val np = NetworkPacket(PACKET_TYPE_NOTIFICATION)
        np["id"] = id
        np["isCancel"] = true
        device.sendPacket(np)
        currentNotifications.remove(id)
        notificationsIcons.remove(id)
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        val key = getNotificationKeyCompat(statusBarNotification)
        synchronized(postedNotificationsLock) {
            postedNotifications.add(key)
        }

        if (sharedPreferences.getBoolean(PREF_NOTIFICATION_SCREEN_OFF, false) || !keyguardManager.isKeyguardLocked) {
            sendNotificationWithDelay(statusBarNotification)
        }
    }

    private fun sendNotificationWithDelay(statusBarNotification: StatusBarNotification) {
        val key = getNotificationKeyCompat(statusBarNotification)
        synchronized(postedNotificationsLock) {
            cancelDelayedNotification(key)
            if (!postedNotifications.contains(key)) {
                return
            }
            val delayedNotification = Message.obtain(mainHandler, Runnable {
                synchronized(postedNotificationsLock) {
                    if (!postedNotifications.contains(key)) {
                        return@Runnable
                    }
                }
                sendNotification(statusBarNotification, false)
            })
            delayedNotification.obj = key
            mainHandler.sendMessageDelayed(delayedNotification, NOTIFICATION_SYNC_DELAY_MS)
        }
    }

    private fun cancelDelayedNotification(key: String) {
        mainHandler.removeCallbacksAndMessages(key)
    }

    // isPreexisting is true for notifications that we are sending in response to a request command
    // and that we want to send with the "silent" flag set
    private fun sendNotification(
        statusBarNotification: StatusBarNotification,
        isPreexisting: Boolean
    ) {
        val notification = statusBarNotification.notification

        if ((notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            || (notification.flags and Notification.FLAG_LOCAL_ONLY) != 0
            || (notification.flags and NotificationCompat.FLAG_GROUP_SUMMARY) != 0 // The notification that groups other notifications
        ) {
            // This is not a notification we want
            return
        }

        val packageName = statusBarNotification.packageName
        if (!appDatabase.isEnabled(packageName)) {
            // Notification excluded by the user
            return
        }
        if ("com.android.systemui" == packageName) {
            if ("low_battery" == statusBarNotification.tag) {
                // HACK: Android low battery notification are posted again every few seconds. Ignore them, as we already have a battery indicator.
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if ("MediaOngoingActivity" == notification.channelId) {
                    // HACK: Samsung OneUI sends this notification when media playback is started. Ignore it, as this is handled by Mpris plugin
                    return
                }
            }
        }

        if ("org.kde.kdeconnect_tp" == packageName || "org.kde.kdeconnect_tp.debug" == packageName) {
            // Don't send our own notifications
            return
        }

        val key = getNotificationKeyCompat(statusBarNotification)
        val isUpdate = currentNotifications.contains(key)

        if (!isUpdate) {
            currentNotifications.add(key)
        }

        val np = NetworkPacket(PACKET_TYPE_NOTIFICATION)

        val appIcon = extractIcon(statusBarNotification, notification)

        if (appIcon != null && !appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)) {
            val iconBytes = getIconBytes(appIcon)
            val iconHash = getIconHash(iconBytes)

            // If it's the same icon, the other end should have it already, so there's no need to send it again.
            if (iconHash != notificationsIcons[key]) {
                np.payload = NetworkPacket.Payload(iconBytes)
                notificationsIcons[key] = iconHash
            }
            // We should always send the icon's hash so the other end can know which icon to use.
            np["payloadHash"] = iconHash
        }

        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

        val isConversation = messagingStyle != null
        val isGroupConversation = isConversation && messagingStyle.isGroupConversation() && messagingStyle.conversationTitle != null

        np["actions"] = extractActions(notification, key)

        np["id"] = key
        np["isClearable"] = statusBarNotification.isClearable
        val appName = appNameLookup(context, packageName)
        np["appName"] = appName ?: packageName
        np["time"] = statusBarNotification.postTime.toString()
        np["silent"] = isPreexisting

        if (!appDatabase.getPrivacy(packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS)) {
            val rn = extractRepliableNotification(statusBarNotification)
            if (rn != null) {
                np["requestReplyId"] = rn.id
                pendingIntents[rn.id] = rn
            }

            np["ticker"] = getTickerText(notification)

            var conversationLength = 0
            if (isConversation) {
                val conversation = extractConversation(messagingStyle)
                conversationLength = conversation.length()
                if (conversationLength != 0) {
                    np["conversation"] = conversation
                }
            }

            // even if it's a conversation, we set `title` and `text` for compatibility.
            if (isGroupConversation) {
                val groupName = messagingStyle.conversationTitle.toString()
                // FIXME: When there're more than one message in group conversation and the user didn't reply, the number of messages appears between "()" next to the name of the group
                // We don't want to show them because Android doesn't show them.
                np["groupName"] = groupName
                // HACK: To differentiate between groups messages and DMs, we set `title` to be the group name and `text` to be `<sender>: <message>`.
                np["title"] = groupName
                val (sender, content) = getMessageAt(messagingStyle, conversationLength - 1)
                np["text"] = "$sender: $content"
            } else {
                val title = extractStringFromExtra(notification.extras, NotificationCompat.EXTRA_TITLE)
                if (title != null) {
                    np["title"] = title
                }
                val text = extractText(notification)
                if (text != null) {
                    np["text"] = text
                }
            }
        }
        device.sendPacket(np)
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras
        if (extras.containsKey(NotificationCompat.EXTRA_BIG_TEXT)) {
            return extractStringFromExtra(extras, NotificationCompat.EXTRA_BIG_TEXT)
        }
        return extractStringFromExtra(extras, NotificationCompat.EXTRA_TEXT)
    }

    private fun getIconBytes(appIcon: Bitmap): ByteArray {
        val outStream = ByteArrayOutputStream()
        appIcon.compress(Bitmap.CompressFormat.PNG, 90, outStream)
        return outStream.toByteArray()
    }

    private fun getIconHash(iconBytes: ByteArray): String {
        return getChecksum(iconBytes)
    }

    private fun getMessageAt(
        messagingStyle: NotificationCompat.MessagingStyle,
        index: Int
    ): Pair<String, String> {
        val message = messagingStyle.messages.getOrNull(index)
        val sender = message?.person?.name?.toString() ?: context.getString(R.string.unknown_sender)
        val content = message?.text?.toString() ?: ""
        return Pair(sender, content)
    }

    private fun extractIcon(
        statusBarNotification: StatusBarNotification,
        notification: Notification
    ): Bitmap? {
        try {
            val foreignContext = context.createPackageContext(statusBarNotification.packageName, 0)
            if (notification.getLargeIcon() != null) {
                return iconToBitmap(foreignContext, notification.getLargeIcon())
            }
            if (notification.smallIcon != null) {
                return iconToBitmap(foreignContext, notification.smallIcon)
            }
            val foreignResources = context.packageManager.getResourcesForApplication(statusBarNotification.packageName)
            val foreignIcon = foreignResources.getDrawable(notification.icon) // Might throw Resources.NotFoundException
            return drawableToBitmap(foreignIcon)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package name not found", e)
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Package not found", e)
        }
        return null
    }

    private fun extractActions(notification: Notification, key: String): JSONArray? {
        val notiActions = notification.actions
        if (notiActions.isNullOrEmpty()) {
            return null
        }

        val jsonArray = JSONArray()
        for (action in notiActions) {
            val title = action.title
                ?: continue

            // Check whether it is a reply action. We have special treatment for them
            if (action.remoteInputs?.isNotEmpty() == true) {
                continue
            }

            jsonArray.put(title.toString())

            // A list is automatically created if it doesn't already exist.
            pendingActions.put(key, action)
        }

        return jsonArray
    }

    private fun extractConversation(messagingStyle: NotificationCompat.MessagingStyle?): JSONArray {
        if (messagingStyle == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return JSONArray()
        }
        val messages = messagingStyle.messages
        val conversation = JSONArray()
        for (message in messages) {
            val sender = message?.person?.name?.toString() ?: context.getString(R.string.unknown_sender)
            val content = message.text?.toString() ?: ""
            conversation.put(JSONObject().apply {
                put("sender", sender)
                put("content", content)
            })
        }
        return conversation
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        drawable ?: return null
        val res = if (drawable.intrinsicWidth > 128 || drawable.intrinsicHeight > 128) {
            createBitmap(96, 96)
        } else if (drawable.intrinsicWidth <= 64 || drawable.intrinsicHeight <= 64) {
            createBitmap(96, 96)
        } else {
            createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
        val canvas = Canvas(res)
        drawable.setBounds(0, 0, res.getWidth(), res.getHeight())
        drawable.draw(canvas)
        return res
    }

    private fun iconToBitmap(foreignContext: Context?, icon: Icon?): Bitmap? {
        icon ?: return null
        return drawableToBitmap(icon.loadDrawable(foreignContext))
    }

    private fun replyToNotification(id: String, message: String) {
        val repliableNotification = pendingIntents[id]
        if (repliableNotification == null) {
            Log.e(TAG, "No such notification")
            return
        }
        val remoteInputs = arrayOfNulls<RemoteInput>(repliableNotification.remoteInputs.size)

        val localIntent = Intent()
        localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val localBundle = Bundle()
        repliableNotification.remoteInputs.forEachIndexed { i, remoteInput ->
            remoteInputs[i] = remoteInput
            localBundle.putCharSequence(remoteInput.resultKey, message)
        }
        RemoteInput.addResultsToIntent(remoteInputs, localIntent, localBundle)

        try {
            repliableNotification.pendingIntent.send(context, 0, localIntent)
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "replyToNotification error: " + e.message)
        }
        pendingIntents.remove(id)
    }

    private fun extractRepliableNotification(statusBarNotification: StatusBarNotification): RepliableNotification? {
        val notiActions = statusBarNotification.notification.actions
            ?: return null

        for (action in notiActions) {
            if (action != null && action.remoteInputs != null && action.actionIntent != null) {
                // Is a reply
                return RepliableNotification(
                    pendingIntent = action.actionIntent,
                    remoteInputs = action.remoteInputs.toList(),
                    packageName = statusBarNotification.packageName,
                )
            }
        }

        return null
    }

    /**
     * Returns the the title and text of the notification.
     */
    private fun getTickerText(notification: Notification): String {
        var ticker = ""

        try {
            val extras = notification.extras
            val extraTitle = extractStringFromExtra(extras, NotificationCompat.EXTRA_TITLE)
            val extraText = extractStringFromExtra(extras, NotificationCompat.EXTRA_TEXT)

            if (extraTitle != null && !TextUtils.isEmpty(extraText)) {
                ticker = "$extraTitle: $extraText"
            } else if (extraTitle != null) {
                ticker = extraTitle
            } else if (extraText != null) {
                ticker = extraText
            }
        } catch (e: Exception) {
            Log.e(TAG, "problem parsing notification extras for " + notification.tickerText, e)
        }

        if (ticker.isEmpty()) {
            ticker = if (notification.tickerText != null) notification.tickerText.toString() else ""
        }

        return ticker
    }

    private fun sendCurrentNotifications(service: NotificationReceiver) {
        if (!NotificationReceiver.hasReadNotificationsPermission(context)) {
            return
        }
        val notifications= try {
            service.getActiveNotifications()
        } catch (_: SecurityException) {
            return
        }
        if (notifications == null) {
            return // Can happen only on API 23 and lower
        }
        for (notification in notifications) {
            sendNotification(notification, true)
        }
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type == PACKET_TYPE_NOTIFICATION_ACTION) {
            val key = np.getString("key")
            val title = np.getString("action")
            var intent: PendingIntent? = null

            for (a in pendingActions.get(key)) {
                if (a.title == title) {
                    intent = a.actionIntent
                    break
                }
            }

            if (intent != null) {
                try {
                    intent.send()
                } catch (e: PendingIntent.CanceledException) {
                    Log.e(TAG, "Triggering action failed", e)
                }
            }
        } else if (np.getBoolean("request")) {
            if (serviceReady) {
                NotificationReceiver.RunCommand(context) { service ->
                    this.sendCurrentNotifications(service)
                }
            }
        } else if (np.has("cancel")) {
            val dismissedId = np.getString("cancel")
            currentNotifications.remove(dismissedId)
            NotificationReceiver.RunCommand(context) { service ->
                service.cancelNotification(dismissedId)
            }
        } else if (np.has("requestReplyId") && np.has("message")) {
            replyToNotification(np.getString("requestReplyId"), np.getString("message"))
        }

        return true
    }

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_notifications)
            .setMessage(R.string.no_permissions)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()

    override val supportedPacketTypes = arrayOf(
            PACKET_TYPE_NOTIFICATION_REQUEST,
            PACKET_TYPE_NOTIFICATION_REPLY,
            PACKET_TYPE_NOTIFICATION_ACTION
        )

    override val outgoingPacketTypes = arrayOf(PACKET_TYPE_NOTIFICATION)


    companion object {
        private const val PACKET_TYPE_NOTIFICATION = "kdeconnect.notification"
        private const val PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request"
        private const val PACKET_TYPE_NOTIFICATION_REPLY = "kdeconnect.notification.reply"
        private const val PACKET_TYPE_NOTIFICATION_ACTION = "kdeconnect.notification.action"
        const val PREFERENCE_KEY = "prefKey"
        const val PREF_NOTIFICATION_SCREEN_OFF = "pref_notification_screen_off"
        private const val NOTIFICATION_SYNC_DELAY_MS = 50L

        private const val TAG = "KDE/NotificationsPlugin"

        private fun extractStringFromExtra(extras: Bundle, key: String): String? {
            val extra = extras.get(key)
            return when (extra) {
                null -> null
                is String -> extra
                is SpannableString -> extra.toString()
                else -> {
                    Log.e(TAG, "Don't know how to extract text from extra of type: " + extra.javaClass.getCanonicalName())
                    null
                }
            }
        }

        private fun getNotificationKeyCompat(statusBarNotification: StatusBarNotification): String {
            // first check if it's one of our remoteIds
            val tag = statusBarNotification.tag
            return if (tag != null && tag.startsWith("kdeconnectId:")) {
                statusBarNotification.id.toString()
            } else {
                statusBarNotification.key
            }
        }

        private fun getChecksum(data: ByteArray): String {
            val md = MessageDigest.getInstance("MD5")
            md.update(data)
            return bytesToHex(md.digest())
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val hexArray = "0123456789ABCDEF".toCharArray()
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars).lowercase(Locale.getDefault())
        }
    }
}
