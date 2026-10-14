/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.clipboard

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.Html
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardListener.ClipboardObserver
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class ClipboardPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_clipboard)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_clipboard_desc)

    override fun hasSettings(): Boolean = true

    override fun supportsDeviceSpecificSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment {
        return PluginSettingsFragment.newInstance(pluginKey, device.deviceId, R.xml.clipboardplugin_preferences)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        val content = np.getString("content")
        when (np.type) {
            (PACKET_TYPE_CLIPBOARD) -> {
                ClipboardSyncManager.applyIncoming(
                    context, content, np.getString(ECOSYSTEM_CLIPBOARD_ID), device.name,
                    np.getBoolean(ECOSYSTEM_CLIPBOARD_SENSITIVE, false),
                )
                return true
            }
            (PACKET_TYPE_CLIPBOARD_CONNECT) -> {
                val packetTime = np.getLong("timestamp")
                // If the packetTime is 0, it means the timestamp is unknown (so do nothing).
                if (packetTime == 0L || packetTime < ClipboardListener.instance(context).updateTimestamp) {
                    return false
                }
                if ("content" in np) { // change clipboard if content is in NetworkPacket
                    ClipboardSyncManager.applyIncoming(
                        context, content, np.getString(ECOSYSTEM_CLIPBOARD_ID), device.name,
                        np.getBoolean(ECOSYSTEM_CLIPBOARD_SENSITIVE, false),
                    )
                }
                return true
            }
            else -> throw UnsupportedOperationException("Unknown packet type: ${np.type}")
        }
    }

    private val observer: ClipboardObserver = object : ClipboardObserver {
        override fun clipboardChanged(content: String, contentType: ClipboardListener.ClipboardContentType, plainText: Boolean) {
            if (contentType == ClipboardListener.ClipboardContentType.Password) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ClipboardSyncManager.onLocalClipboardChanged(
                    content,
                    contentType == ClipboardListener.ClipboardContentType.Password,
                    plainText,
                )
            } else {
                propagateClipboard(content)
            }
        }
    }

    @VisibleForTesting
    fun propagateClipboard(content: String) {
        val np = NetworkPacket(PACKET_TYPE_CLIPBOARD)
        np["content"] = content
        device.sendPacket(np)
    }

    internal fun sendAutomaticClipboard(content: String, id: String) {
        val np = NetworkPacket(PACKET_TYPE_CLIPBOARD)
        np["content"] = content
        np[ECOSYSTEM_CLIPBOARD_ID] = id
        device.sendPacket(np)
    }

    private fun sendConnectPacket() {
        val content = ClipboardListener.instance(context).currentContent ?: return // Send clipboard only if it had been initialized
        if (ClipboardListener.instance(context).currentContentType == ClipboardListener.ClipboardContentType.Password) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !canSyncAutomatically(context)) return
        val np = NetworkPacket(PACKET_TYPE_CLIPBOARD_CONNECT)
        val timestamp = ClipboardListener.instance(context).updateTimestamp
        np["timestamp"] = timestamp
        np["content"] = content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            np[ECOSYSTEM_CLIPBOARD_ID] = ClipboardSyncManager.contentId(content)
        }
        device.sendPacket(np)
    }


    override fun onCreate() {
        ClipboardListener.instance(context).registerObserver(observer)
        sendConnectPacket()
    }

    override fun onDestroy() {
        ClipboardListener.instance(context).removeObserver(observer)
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT)

    override fun getUiButtons(): List<PluginUiButton> {
        return if (!canSyncAutomatically(context)) {
            listOf(PluginUiButton(context.getString(R.string.send_clipboard), R.drawable.ic_baseline_content_paste_24) { _: Activity? ->
                userInitiatedSendClipboard()
            })
        } else {
            emptyList()
        }
    }

    override fun getUiMenuEntries(): List<PluginUiMenuEntry> {
        return if (canSyncAutomatically(context)) {
            // If automatic sync is supported, add the manual action less prominently, as a menu entry instead of a button.
            listOf(PluginUiMenuEntry(context.getString(R.string.send_clipboard)) { _: Activity? ->
                userInitiatedSendClipboard()
            })
        } else {
            emptyList()
        }
    }

    private fun userInitiatedSendClipboard() {
        if (isDeviceInitialized) {
            val clipboardManager = context.getSystemService<ClipboardManager>()!!
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip!!
                if (ClipboardListener.detectContentType(clip) == ClipboardListener.ClipboardContentType.Password) return
                val plainText = clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                val htmlText = clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                if (!plainText && !htmlText) return
                if (ClipboardSyncPreferences.read(context).plainTextOnly && !plainText) return
                val item = clip.getItemAt(0)
                val content = item.text?.toString()
                    ?: item.htmlText?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                    ?: return
                if (content.isBlank()) return
                this.propagateClipboard(content)
                Toast.makeText(this.context, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /**
         * Packet containing just clipboard contents, sent when a device updates its clipboard.
         *
         *
         * The body should look like so:
         * {
         * "content": "password"
         * }
         */
        private const val PACKET_TYPE_CLIPBOARD = "kdeconnect.clipboard"
        private const val ECOSYSTEM_CLIPBOARD_ID = "ecosystem_clipboard_id"
        private const val ECOSYSTEM_CLIPBOARD_SENSITIVE = "ecosystem_clipboard_sensitive"

        /**
         * Packet containing clipboard contents and a timestamp that the contents were last updated, sent
         * on first connection
         *
         *
         * The timestamp is milliseconds since epoch. It can be 0, which indicates that the clipboard
         * update time is currently unknown.
         *
         *
         * The body should look like so:
         * {
         * "timestamp": 542904563213,
         * "content": "password"
         * }
         */
        private const val PACKET_TYPE_CLIPBOARD_CONNECT = "kdeconnect.clipboard.connect"

        fun canSyncAutomatically(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return true
            }
            return ClipboardSyncManager.isAutomaticAvailable(context)
        }

    }
}
