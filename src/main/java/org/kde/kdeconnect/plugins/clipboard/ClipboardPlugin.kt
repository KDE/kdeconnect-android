/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.clipboard

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardListener.ClipboardObserver
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class ClipboardPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_clipboard)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_clipboard_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        val content = np.getString("content")
        when (np.type) {
            (PACKET_TYPE_CLIPBOARD) -> {
                ClipboardListener.instance(context).setText(content)
                return true
            }
            (PACKET_TYPE_CLIPBOARD_CONNECT) -> {
                val packetTime = np.getLong("timestamp")
                // If the packetTime is 0, it means the timestamp is unknown (so do nothing).
                if (packetTime == 0L || packetTime < ClipboardListener.instance(context).updateTimestamp) {
                    return false
                }
                if ("content" in np) { // change clipboard if content is in NetworkPacket
                    ClipboardListener.instance(context).setText(content)
                }
                return true
            }
            else -> throw UnsupportedOperationException("Unknown packet type: " + np.type)
        }
    }

    private val observer: ClipboardObserver = object : ClipboardObserver {
        override fun clipboardChanged(content: String) {
            return this@ClipboardPlugin.propagateClipboard(content)
        }
    }

    @VisibleForTesting
    fun propagateClipboard(content: String) {
        val np = NetworkPacket(PACKET_TYPE_CLIPBOARD)
        np["content"] = content
        device.sendPacket(np)
    }

    private fun sendConnectPacket() {
        val content = ClipboardListener.instance(context).currentContent ?: return // Send clipboard only if it had been initialized
        val np = NetworkPacket(PACKET_TYPE_CLIPBOARD_CONNECT)
        val timestamp = ClipboardListener.instance(context).updateTimestamp
        np["timestamp"] = timestamp
        np["content"] = content
        device.sendPacket(np)
    }


    override fun onCreate(): Boolean {
        ClipboardListener.instance(context).registerObserver(observer)
        sendConnectPacket()
        return true
    }

    override fun onDestroy() {
        ClipboardListener.instance(context).removeObserver(observer)
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT)

    override fun getUiButtons(): List<PluginUiButton> {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && canAccessLogs()) {
            listOf(PluginUiButton(context.getString(R.string.send_clipboard), R.drawable.ic_baseline_content_paste_24) { _: Activity? ->
                userInitiatedSendClipboard()
            })
        } else {
            emptyList()
        }
    }

    override fun getUiMenuEntries(): List<PluginUiMenuEntry> {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && !canAccessLogs()) {
            listOf(PluginUiMenuEntry(context.getString(R.string.send_clipboard)) { _: Activity? ->
                userInitiatedSendClipboard()
            })
        } else {
            emptyList()
        }
    }

    private fun userInitiatedSendClipboard() {
        if (isDeviceInitialized) {
            val clipboardManager = ContextCompat.getSystemService<ClipboardManager>(this.context, ClipboardManager::class.java)
            val item: ClipData.Item
            if (clipboardManager!!.hasPrimaryClip()) {
                item = clipboardManager.primaryClip!!.getItemAt(0)
                val content = item.coerceToText(this.context).toString()
                this.propagateClipboard(content)
                Toast.makeText(this.context, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun canAccessLogs(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_DENIED
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
    }
}
