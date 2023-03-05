/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.RunCommandPlugin

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.CommandAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.StatelessTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import io.reactivex.Flowable
import io.reactivex.processors.ReplayProcessor
import org.json.JSONArray
import org.json.JSONException
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.UserInterface.MainActivity
import org.kde.kdeconnect_tp.R
import org.reactivestreams.FlowAdapters
import java.util.*
import java.util.concurrent.Flow
import java.util.function.Consumer

private class CommandEntryWithDevice(name: String, cmd: String, key: String, val device: Device) : CommandEntry(name, cmd, key)

@RequiresApi(Build.VERSION_CODES.R)
class RunCommandControlsProviderService : ControlsProviderService() {
    private lateinit var updatePublisher: ReplayProcessor<Control>
    private lateinit var sharedPreferences: SharedPreferences

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return FlowAdapters.toFlowPublisher(Flowable.fromIterable(getAllCommandsList().map { commandEntry ->
            Control.StatelessBuilder(commandEntry.device.deviceId + ":" + commandEntry.key, getIntent(commandEntry.device))
                .setTitle(commandEntry.name)
                .setSubtitle(commandEntry.command)
                .setStructure(commandEntry.device.name)
                .setCustomIcon(Icon.createWithResource(this, R.drawable.run_command_plugin_icon_24dp))
                .build()
        }))
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        updatePublisher = ReplayProcessor.create()

        for (controlId in controlIds) {
            val commandEntry = getCommandByControlId(controlId)
            if (commandEntry != null && commandEntry.device.isReachable) {
                updatePublisher.onNext(Control.StatefulBuilder(controlId, getIntent(commandEntry.device))
                        .setTitle(commandEntry.name)
                        .setSubtitle(commandEntry.command)
                        .setStructure(commandEntry.device.name)
                        .setStatus(Control.STATUS_OK)
                        .setStatusText(getString(R.string.tap_to_execute))
                        .setControlTemplate(StatelessTemplate(commandEntry.key))
                        .setCustomIcon(Icon.createWithResource(this, R.drawable.run_command_plugin_icon_24dp))
                        .build())
            } else if (commandEntry != null && commandEntry.device.isPaired && !commandEntry.device.isReachable) {
                updatePublisher.onNext(Control.StatefulBuilder(controlId, getIntent(commandEntry.device))
                        .setTitle(commandEntry.name)
                        .setSubtitle(commandEntry.command)
                        .setStructure(commandEntry.device.name)
                        .setStatus(Control.STATUS_DISABLED)
                        .setControlTemplate(StatelessTemplate(commandEntry.key))
                        .setCustomIcon(Icon.createWithResource(this, R.drawable.run_command_plugin_icon_24dp))
                        .build())
            } else {
                updatePublisher.onNext(Control.StatefulBuilder(controlId, getIntent(commandEntry?.device))
                        .setStatus(Control.STATUS_NOT_FOUND)
                        .build())
            }
        }

        return FlowAdapters.toFlowPublisher(updatePublisher)
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        if (!this::updatePublisher.isInitialized) {
            updatePublisher = ReplayProcessor.create()
        }

        if (action is CommandAction) {
            val commandEntry = getCommandByControlId(controlId)
            if (commandEntry != null) {
                val plugin = BackgroundService.getInstance().getDevice(controlId.split(":")[0]).getPlugin(RunCommandPlugin::class.java)
                if (plugin != null) {
                    BackgroundService.RunCommand(this) {
                        plugin.runCommand(commandEntry.key)
                    }
                    
                    consumer.accept(ControlAction.RESPONSE_OK)
                } else {
                    consumer.accept(ControlAction.RESPONSE_FAIL)
                }

                updatePublisher.onNext(Control.StatefulBuilder(controlId, getIntent(commandEntry.device))
                        .setTitle(commandEntry.name)
                        .setSubtitle(commandEntry.command)
                        .setStructure(commandEntry.device.name)
                        .setStatus(Control.STATUS_OK)
                        .setStatusText(getString(R.string.tap_to_execute))
                        .setControlTemplate(StatelessTemplate(commandEntry.key))
                        .setCustomIcon(Icon.createWithResource(this, R.drawable.run_command_plugin_icon_24dp))
                        .build())
            }
        }
    }

    private fun getSavedCommandsList(device: Device): List<CommandEntryWithDevice> {
        if (!this::sharedPreferences.isInitialized) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        }

        val commandList = mutableListOf<CommandEntryWithDevice>()

        return try {
            val jsonArray = JSONArray(sharedPreferences.getString(RunCommandPlugin.KEY_COMMANDS_PREFERENCE + device.deviceId, "[]"))

            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(index)
                commandList.add(CommandEntryWithDevice(jsonObject.getString("name"), jsonObject.getString("command"), jsonObject.getString("key"), device))
            }

            commandList
        } catch (error: JSONException) {
            Log.e("RunCommand", "Error parsing JSON", error)
            listOf()
        }
    }

    private fun getAllCommandsList(): List<CommandEntryWithDevice> {
        val commandList = mutableListOf<CommandEntryWithDevice>()

        val service = BackgroundService.getInstance() ?: return commandList

        for (device in service.devices.values) {
            if (!device.isReachable) {
                commandList.addAll(getSavedCommandsList(device))
                continue
            } else if (!device.isPaired) {
                continue
            }

            val plugin = device.getPlugin(RunCommandPlugin::class.java)
            if (plugin != null) {
                for (jsonObject in plugin.commandList) {
                    try {
                        commandList.add(CommandEntryWithDevice(jsonObject.getString("name"), jsonObject.getString("command"), jsonObject.getString("key"), device))
                    } catch (error: JSONException) {
                        Log.e("RunCommand", "Error parsing JSON", error)
                    }
                }
            }
        }

        return commandList
    }

    private fun getCommandByControlId(controlId: String): CommandEntryWithDevice? {
        val controlIdParts = controlId.split(":")

        val service = BackgroundService.getInstance() ?: return null

        val device = service.getDevice(controlIdParts[0])

        if (device == null || !device.isPaired) return null

        val commandList = if (device.isReachable) {
            device.getPlugin(RunCommandPlugin::class.java)?.commandList?.map { jsonObject ->
                CommandEntryWithDevice(jsonObject.getString("name"), jsonObject.getString("command"), jsonObject.getString("key"), device)
            }
        } else {
            getSavedCommandsList(device)
        }

        return commandList?.find { command ->
            try {
                command.key == controlIdParts[1]
            } catch (error: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", error)
                false
            }
        }
    }

    private fun getIntent(device: Device?): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).setClass(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(MainActivity.EXTRA_DEVICE_ID, device?.deviceId)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}