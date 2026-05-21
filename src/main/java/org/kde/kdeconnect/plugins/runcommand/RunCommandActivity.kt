/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2026 Johann Specht <sajeg.dev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import android.content.ClipData
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect_tp.R


class RunCommandActivity : AppCompatActivity() {

    val commandList = mutableStateListOf<CommandEntry>()

    private var deviceId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RunCommandActivity", "Launched")
        deviceId = this.intent.getStringExtra("deviceId")
        val plugin =
            getInstance().getDevicePlugin(deviceId, RunCommandPlugin::class.java)
        if (plugin == null) {
            finish()
            return
        }

        for (obj in plugin.commandList) {
            try {
                commandList.add(CommandEntry(obj))
            } catch (e: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", e)
            }
        }
        commandList.sortBy { it.name }
        val device = getInstance().getDevice(deviceId) ?: return

        setContent {
            val clipboardManager = LocalClipboard.current

            RunCommandScreen(
                plugin = plugin,
                device = device,
                commandList = commandList,
                onBackPressedDispatcher = onBackPressedDispatcher,
                onCopyUrlToClipboard = {
                    copyCommandToClipboard(device, it, clipboardManager)
                },
                onUpdate = {
                    updateList()
                }
            )
        }
    }

    private fun copyCommandToClipboard(
        device: Device,
        command: CommandEntry,
        clipboardManager: Clipboard
    ) {
        val url =
            "kdeconnect://runcommand/" + device.deviceId + "/" + command.key
        val clipData = ClipData.newPlainText("Command", url)

        CoroutineScope(Dispatchers.IO).launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
        }
        val toast = Toast.makeText(
            this,
            R.string.clipboard_toast,
            Toast.LENGTH_SHORT
        )
        toast.show()
    }


    private fun updateList() {
        commandList.removeAll(commandList)
        val plugin =
            getInstance().getDevicePlugin(deviceId, RunCommandPlugin::class.java) ?: return

        for (obj in plugin.commandList) {
            try {
                commandList.add(CommandEntry(obj))
            } catch (e: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", e)
            }
        }
        commandList.sortBy { it.name }
    }

    override fun onResume() {
        super.onResume()
        val plugin =
            getInstance().getDevicePlugin(deviceId, RunCommandPlugin::class.java)
        if (plugin == null) {
            finish()
            return
        }
    }
}