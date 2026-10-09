/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.runcommand

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class RunCommandPlugin : Plugin() {

    val commandList = ArrayList<JSONObject>()
    private val callbacks = ArrayList<CommandsChangedCallback>()
    val commandItems = ArrayList<CommandEntry>()
    val output = SnapshotStateList<RunCommandOutput>()

    // Ids of the commands that are currently running on the remote device. Currently only
    // used to show/hide the stop button (when this is empty).
    private val runningProcesses = LinkedHashSet<Int>()
    // Position in `output` of the "$ command" line for each running/finished command id, so
    // we can go back and update its color once we know whether it succeeded or failed.
    private val commandLineIndexById = HashMap<Int, Int>()

    private lateinit var sharedPreferences: SharedPreferences
    private var canAddCommand = false

    fun addCommandsUpdatedCallback(newCallback: CommandsChangedCallback) {
        callbacks.add(newCallback)
    }

    fun removeCommandsUpdatedCallback(theCallback: CommandsChangedCallback) {
        callbacks.remove(theCallback)
    }

    fun interface CommandsChangedCallback {
        fun update()
    }

    val commandRunning: MutableState<Boolean> = mutableStateOf(false)

    fun clearOutput() {
        output.clear()
        commandLineIndexById.clear()
    }

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_runcommand)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_runcommand_desc)

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment {
        return PluginSettingsFragment.newInstance(pluginKey, device.deviceId, R.xml.runcommand_preferences)
    }

    override fun getUiButtons(): List<PluginUiButton> {
        return listOf(PluginUiButton(context.getString(R.string.pref_plugin_runcommand), R.drawable.run_command_plugin_icon_24dp) { parentActivity ->
            val intent = Intent(parentActivity, RunCommandActivity::class.java).apply {
                putExtra(RunCommandActivity.EXTRA_DEVICE_ID, device.deviceId)
            }
            parentActivity.startActivity(intent)
        })
    }

    override fun onCreate() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        requestCommandList()
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.has("commandList")) {
            commandList.clear()
            try {
                commandItems.clear()
                val obj = JSONObject(np.getString("commandList"))
                for (s in obj.keys()) {
                    val o = obj.getJSONObject(s)
                    o.put("key", s)
                    commandList.add(o)

                    try {
                        commandItems.add(
                            CommandEntry(o)
                        )
                    } catch (e: JSONException) {
                        Log.e("RunCommand", "Error parsing JSON", e)
                    }
                }

                commandItems.sortBy { it.name }

                // Used only by RunCommandControlsProviderService to display controls correctly even when device is not available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val array = JSONArray()

                    for (command in commandList) {
                        array.put(command)
                    }

                    sharedPreferences.edit()
                        .putString(KEY_COMMANDS_PREFERENCE + device.deviceId, array.toString())
                        .apply()
                }

                forceRefreshWidgets(context)

            } catch (e: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", e)
            }

            for (aCallback in callbacks) {
                aCallback.update()
            }

            device.onPluginsChanged()

            canAddCommand = np.getBoolean("canAddCommand", false)

            return true
        } else if (np.has("commandStarted")) {
            val id = np.getInt("id")
            val command = np.getString("command")

            Log.i("RunCommandPlugin", "commandStarted $id")

            runningProcesses.add(id)
            commandRunning.value = true

            output.add(RunCommandOutput(RunCommandStatus.COMMAND_RUNNING, "$ $command", id))
            commandLineIndexById[id] = output.size - 1

            return true
        } else if (np.has("commandOutput")) {
            val stdOut = np.getStringList("stdout") ?: emptyList()
            val stdErr = np.getStringList("stderr") ?: emptyList()
            val id = np.getInt("id")
            for (line in stdOut) {
                Log.d("STDOUT", "Line:$line")
                output.add(RunCommandOutput(RunCommandStatus.STDOUT, line, id))
            }
            for (line in stdErr) {
                Log.d("STDERR", "Line:$line")
                output.add(RunCommandOutput(RunCommandStatus.STDERR, line, id))
            }

            return true
        } else if (np.has("commandFinished")) {
            val id = np.getInt("id")
            val success = np.getBoolean("success", true)

            Log.i("RunCommandPlugin", "commandFinished $id")
            runningProcesses.remove(id)
            commandRunning.value = runningProcesses.isNotEmpty()

            val index = commandLineIndexById.remove(id)
            if (index != null && index < output.size) {
                val commandLine = output[index]
                // Build a brand-new instance instead of mutating commandLine in place: SnapshotStateList
                // doesn't notify Compose when calling output.set() with the same (mutated) reference.
                output[index] = commandLine.copy(
                    commandStatus = if (success) RunCommandStatus.COMMAND_SUCCESSFUL else RunCommandStatus.COMMAND_FAILED
                )
            }

            return true
        }
        return false
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_RUNCOMMAND, PACKET_TYPE_RUNCOMMAND_OUTPUT)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_RUNCOMMAND_REQUEST)

    fun runCommand(cmdKey: String?) {
        if (cmdKey == null) return
        Log.d("RunCommand", "Sending $cmdKey")
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["key"] = cmdKey
        device.sendPacket(np)
    }

    private fun requestCommandList() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["requestCommandList"] = true
        device.sendPacket(np)
    }

    fun canAddCommand(): Boolean = canAddCommand

    internal fun sendSetupPacket() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["setup"] = true
        device.sendPacket(np)
    }

    internal fun sendStop() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["stop"] = true
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_RUNCOMMAND = "kdeconnect.runcommand"
        const val PACKET_TYPE_RUNCOMMAND_OUTPUT = "kdeconnect.runcommand.output"
        private const val PACKET_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request"
        const val KEY_COMMANDS_PREFERENCE = "commands_preference_"
    }
}
