/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect.async.BackgroundJobHandler
import org.kde.kdeconnect.async.BackgroundJobHandler.Companion.newFixedThreadPoolBackgroundJobHandler
import org.kde.kdeconnect.helpers.FilesHelper.uriToNetworkPacket
import org.kde.kdeconnect.helpers.IntentHelper.startActivityFromBackgroundOrCreateNotification
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R
import androidx.core.content.edit
import androidx.core.net.toUri
import org.kde.kdeconnect.helpers.IntentHelper
import org.kde.kdeconnect.helpers.ThreadHelper

/**
 * A Plugin for sharing and receiving files and uris.
 * 
 * 
 * All of the associated I/O work is scheduled on background
 * threads by [BackgroundJobHandler].
 * 
 */
@LoadablePlugin
class SharePlugin : Plugin() {
    private val backgroundJobHandler: BackgroundJobHandler = newFixedThreadPoolBackgroundJobHandler(5)
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val receiveFileJobCallback: Callback = this.Callback()
    private var receiveFileJob: CompositeReceiveFileJob? = null
    private var uploadFileJob: CompositeUploadFileJob? = null


    override fun onCreate(): Boolean {
        createOrUpdateDynamicShortcut()
        // Deliver URLs previously shared to this device now that it's connected
        deliverPreviouslySentIntents()
        return true
    }

    override fun onDestroy() {
        for (shortcut in ShortcutManagerCompat.getDynamicShortcuts(context)) {
            if (shortcut.id != device.deviceId) continue
            if (!device.isReachable && shortcut.isPinned) {
                // Create an updated shortcut with the same ID
                createOrUpdateDynamicShortcut(shortcut)
                break
            } else {
                ShortcutManagerCompat.removeLongLivedShortcuts(context,listOf(shortcut.id))
            }
        }
        super.onDestroy()
    }

    private fun createOrUpdateDynamicShortcut(shortcutToUpdate: ShortcutInfoCompat? = null) {
        val icon = IconCompat.createWithResource(
            context, device.deviceType.toShortcutDrawableId()
        )
        val shortcutIntent: Intent = shortcutToUpdate?.intent
            ?: Intent(context, MainActivity::class.java).apply {
                setAction(Intent.ACTION_VIEW)
                putExtra(MainActivity.EXTRA_DEVICE_ID, device.deviceId)
            }
        val shortcut = ShortcutInfoCompat.Builder(context, device.deviceId)
            .setIntent(shortcutIntent)
            .setIcon(icon)
            .setShortLabel(
                if (shortcutToUpdate == null) {
                    device.name
                } else {
                    context.getString(
                        R.string.unreachable_device_dynamic_shortcut,
                        shortcutToUpdate.shortLabel
                    )
                }
            )
            .setCategories(
                if (shortcutToUpdate == null) {
                    setOf("org.kde.kdeconnect.category.SHARE_TARGET")
                } else {
                    shortcutToUpdate.categories ?: emptySet()
                }
            )
            .setLocusId(
                if (shortcutToUpdate == null)
                    LocusIdCompat(device.deviceId)
                else
                    shortcutToUpdate.locusId
            )
            .build()
        if (shortcutToUpdate == null) {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } else {
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        }
    }

    private fun deliverPreviouslySentIntents() {
        val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        val currentUrlSet = mSharedPrefs.getStringSet(KEY_UNREACHABLE_URL_LIST + device.deviceId, null)
        if (currentUrlSet != null) {
            sendUrls(currentUrlSet.toList())
            mSharedPrefs.edit {
                putStringSet(KEY_UNREACHABLE_URL_LIST + device.deviceId, null)
            }
        }
    }

    override val optionalPermissionExplanation: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.string.share_notifications_explanation
        } else {
            R.string.share_optional_permission_explanation
        }

    override val displayName: String
        get() = context.resources
            .getString(R.string.pref_plugin_sharereceiver)

    override val description: String
        get() = context.resources
            .getString(R.string.pref_plugin_sharereceiver_desc)

    override fun getUiButtons(): kotlin.collections.List<PluginUiButton> {
        return listOf(
            PluginUiButton(
                context.getString(R.string.send_files),
                R.drawable.share_plugin_action_24dp
            ) { parentActivity: Activity ->
                val intent = Intent(parentActivity, SendFileActivity::class.java)
                intent.putExtra("deviceId", device.deviceId)
                parentActivity.startActivity(intent)
            })
    }

    override fun hasSettings(): Boolean = true

    @WorkerThread
    override fun onPacketReceived(np: NetworkPacket): Boolean {
        try {
            if (np.type == PACKET_TYPE_SHARE_REQUEST_UPDATE) {
                receiveFileJob
                    ?.takeIf { it.isRunning }
                    ?.updateTotals(np.getInt(KEY_NUMBER_OF_FILES), np.getLong(KEY_TOTAL_PAYLOAD_SIZE))
                return true
            }
            if (np.has("filename")) {
                receiveFile(np)
            } else if (np.has("text")) {
                receiveText(np)
            } else if (np.has("url")) {
                receiveUrl(np)
            } else {
                Log.e(TAG, "Error: Nothing attached!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception")
            e.printStackTrace()
        }
        return true
    }

    private fun receiveUrl(np: NetworkPacket) {
        val url = np.getString("url")
        Log.i(TAG, "hasUrl: $url")
        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityFromBackgroundOrCreateNotification(context, browserIntent, url)
    }

    private fun receiveText(np: NetworkPacket) {
        val cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)!!
        cm.text = np.getString("text")
        handler.post {
            Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show()
        }
    }

    @WorkerThread
    private fun receiveFile(np: NetworkPacket) {
        val hasNumberOfFiles = np.has(KEY_NUMBER_OF_FILES)
        val isOpen = np.getBoolean("open", false)

        val job = receiveFileJob?.takeIf { hasNumberOfFiles && !isOpen }
            ?: CompositeReceiveFileJob(device, receiveFileJobCallback)

        if (!hasNumberOfFiles) {
            np[KEY_NUMBER_OF_FILES] = 1
            np[KEY_TOTAL_PAYLOAD_SIZE] = np.payloadSize
        }

        job.addNetworkPacket(np)

        if (job !== receiveFileJob) {
            if (hasNumberOfFiles && !isOpen) {
                receiveFileJob = job
            }
            backgroundJobHandler.runJob(job)
        }
    }

    fun sendUrls(urls: List<String>) {
        for (url in urls) {
            val np = NetworkPacket(PACKET_TYPE_SHARE_REQUEST)
            np["url"] = url
            device.sendPacket(np)
        }
    }

    fun sendText(text: String) {
        val np = NetworkPacket(PACKET_TYPE_SHARE_REQUEST)
        np["text"] = text
        device.sendPacket(np)
    }

    fun sendFiles(uriList: List<Uri>) {
        val job = uploadFileJob ?:
        CompositeUploadFileJob(device, this.receiveFileJobCallback)

        //Read all the data early, as we only have permissions to do it while the activity is alive
        for (uri in uriList) {
            val np = uriToNetworkPacket(context, uri, PACKET_TYPE_SHARE_REQUEST)

            if (np != null) {
                job.addNetworkPacket(np)
            }
        }

        if (job !== uploadFileJob) {
            uploadFileJob = job
            backgroundJobHandler.runJob(job)
        }
    }

    fun share(intent: Intent) {
        val streams = IntentHelper.streamsFromIntent(intent)
        if (streams.isNotEmpty()) {
            Log.i(TAG, "Intent contains files to share")
            ThreadHelper.execute { sendFiles(streams) }
            return
        }
        val urls = IntentHelper.parseIntentUrls(intent)
        if (urls.isNotEmpty()) {
            Log.i(TAG, "Intent contains URLs to share")
            sendUrls(urls)
            return
        }
        val text = intent.extras?.getString(Intent.EXTRA_TEXT)
        if (!text.isNullOrEmpty()) {
            Log.i(TAG, "Intent contains text to share")
            sendText(text)
            return
        }
        Log.e(TAG, "There's nothing we know how to share")
    }

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment =
        ShareSettingsFragment.newInstance(pluginKey, R.xml.shareplugin_preferences)

    override val supportedPacketTypes= arrayOf(
        PACKET_TYPE_SHARE_REQUEST,
        PACKET_TYPE_SHARE_REQUEST_UPDATE
    )

    override val outgoingPacketTypes = arrayOf(PACKET_TYPE_SHARE_REQUEST)

    override val optionalPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    private fun jobDone(job: BackgroundJob<*, *>) {
        if (job === receiveFileJob) {
            receiveFileJob = null
        } else if (job === uploadFileJob) {
            uploadFileJob = null
        }
    }

    private inner class Callback : BackgroundJob.Callback<Void?> {
        override fun onResult(job: BackgroundJob<*, *>, result: Void?) = jobDone(job)
        override fun onError(job: BackgroundJob<*, *>, error: Throwable) = jobDone(job)
    }

    fun cancelJob(jobId: Long) {
        if (backgroundJobHandler.isRunning(jobId)) {
            val job = backgroundJobHandler.getJob(jobId)
            if (job != null) {
                job.cancel()
                jobDone(job)
            }
        }
    }

    override fun onDeviceUnpaired(context: Context, deviceId: String) {
        Log.i(TAG, "onDeviceUnpaired deviceId = $deviceId")
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            remove(KEY_UNREACHABLE_URL_LIST + deviceId)
        }
    }

    companion object {
        const val TAG : String = "SharePlugin"

        const val ACTION_CANCEL_SHARE: String = "org.kde.kdeconnect.plugins.share.CancelShare"
        const val CANCEL_SHARE_DEVICE_ID_EXTRA: String = "deviceId"
        const val CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA: String = "backgroundJobId"

        private const val PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request"
        const val PACKET_TYPE_SHARE_REQUEST_UPDATE: String = "kdeconnect.share.request.update"

        const val KEY_NUMBER_OF_FILES: String = "numberOfFiles"
        const val KEY_TOTAL_PAYLOAD_SIZE: String = "totalPayloadSize"

        const val KEY_UNREACHABLE_URL_LIST: String = "key_unreachable_url_list"
    }
}
