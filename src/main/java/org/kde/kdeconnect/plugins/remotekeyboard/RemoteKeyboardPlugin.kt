/*
 * SPDX-FileCopyrightText: 2026 Johann Specht <sajeg.dev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.remotekeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverService
import org.kde.kdeconnect.plugins.remotekeyboardime.RemoteKeyboardService
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R


@RequiresApi(Build.VERSION_CODES.O)
@LoadablePlugin
class RemoteKeyboardPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_remotekeyboard_accessibility)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_remotekeyboard_desc)

    override val minSdk: Int
        get() = Build.VERSION_CODES.O

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_remotekeyboard_desc)
            .setMessage(R.string.no_permissions_remotekeyboard_accessibility)
            .setPositiveButton(R.string.open_accessibility_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()

    companion object {
        const val PACKET_TYPE_MOUSEPAD_REQUEST: String = "kdeconnect.mousepad.request"
        const val PACKET_TYPE_MOUSEPAD_ECHO: String = "kdeconnect.mousepad.echo"
        const val PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE: String = "kdeconnect.mousepad.keyboardstate"

        private const val LOG_TAG = "RemoteKeyboardPlugin"
    }

    override fun checkRequiredPermissions(): Boolean {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        return enabledServices.any { enabledService ->
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            serviceInfo.packageName == context.packageName &&
                serviceInfo.name == MouseReceiverService::class.java.name
        }
    }

    override fun onCreate(): Boolean {
        sendState()
        return super.onCreate()
    }

    override fun onDestroy() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
        np.set("state", value = false)
        device.sendPacket(np)
        super.onDestroy()
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type != PACKET_TYPE_MOUSEPAD_REQUEST) {
            Log.e(
                LOG_TAG,
                "Invalid packet type for RemoteKeyboardPlugin: " + np.type
            )
            return false
        }

        // Check if this package is for the Mouse Receiver
        if (!(np.has("key") || np.has("specialKey"))) {
            return false
        }

        if (RemoteKeyboardService.instance?.visible == true) {
            return false // This packet will be handled by the RemoteKeyboardIMEPlugin instead, silently ignore
        }

        if (!handleKey(np)) {
            Log.i(LOG_TAG, "Could not handle event!")
            return false
        }

        if (np.getBoolean("sendAck")) {
            sendAck(np)
        }

        return true
    }

    private fun sendAck(np: NetworkPacket) {
        val reply = NetworkPacket(PACKET_TYPE_MOUSEPAD_ECHO)
        reply["key"] = np.getString("key")
        if (np.has("specialKey")) {
            reply["specialKey"] = np.getInt("specialKey")
        }
        if (np.has("shift")) {
            reply["shift"] = np.getBoolean("shift")
        }
        if (np.has("ctrl")) {
            reply["ctrl"] = np.getBoolean("ctrl")
        }
        if (np.has("alt")) {
            reply["alt"] = np.getBoolean("alt")
        }
        if (np.has("super")) {
            reply["super"] = np.getBoolean("super")
        }
        reply["isAck"] = true
        device.sendPacket(reply)
    }

    private fun handleKey(np: NetworkPacket): Boolean {
        val key = np.getString("key")
        val sKey = np.getInt("specialKey")

        if (key == "" && sKey == 0) {
            return false
        }

        if (np.getBoolean("shift") || np.getBoolean("ctrl")) {
            multiKey(
                key, sKey, np.getBoolean("shift"), np.getBoolean("ctrl")
            )
        } else if (sKey != 0) {
            specialKey(sKey)
        } else {
            keyInput(key)
        }
        return true
    }

    override val supportedPacketTypes: Array<String> =
        arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST)

    override val outgoingPacketTypes: Array<String> = arrayOf(
        PACKET_TYPE_MOUSEPAD_ECHO,
        PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
    )

    private fun multiKey(
        key: String,
        sKey: Int,
        shift: Boolean,
        ctrl: Boolean
    ) {
        val specialKey = SpecialKeys.fromInt(sKey)
        val focus = RemoteKeyboardInputService.window?.findFocus(FOCUS_INPUT)

        if (shift) {
            val movement = if (ctrl) Movement.WORD else Movement.CHARACTER
            when (specialKey) {
                SpecialKeys.DPAD_LEFT -> moveCursor(
                    forward = false,
                    movement,
                    makeSelection = true
                )

                SpecialKeys.DPAD_RIGHT -> moveCursor(
                    forward = true,
                    movement,
                    makeSelection = true
                )

                SpecialKeys.MOVE_HOME, SpecialKeys.DPAD_UP -> moveCursor(
                    forward = false,
                    Movement.LINE,
                    makeSelection = true
                )

                SpecialKeys.MOVE_END, SpecialKeys.DPAD_DOWN -> moveCursor(
                    forward = true,
                    Movement.LINE,
                    makeSelection = true
                )

                // We already get the letters capitalized or as special characters.
                SpecialKeys.NO_KEY -> keyInput(key)

                else -> {}
            }
        } else if (ctrl) {
            when (key) {
                "c" -> focus?.performAction(AccessibilityNodeInfo.ACTION_COPY)
                "v" -> focus?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                "x" -> focus?.performAction(AccessibilityNodeInfo.ACTION_CUT)
                "a" -> focus?.setSelection(0, focus.text?.length ?: 0)
            }
            when (specialKey) {
                SpecialKeys.DPAD_LEFT -> moveCursor(forward = false, Movement.WORD)
                SpecialKeys.DPAD_RIGHT -> moveCursor(forward = true, Movement.WORD)
                SpecialKeys.DEL -> delete(forward = false, words = true)
                SpecialKeys.FORWARD_DEL -> delete(forward = true, words = true)
                SpecialKeys.ENTER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        focus?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    }
                }

                else -> {}
            }
        }
    }

    private fun specialKey(sKey: Int) {
        val focus = RemoteKeyboardInputService.window?.findFocus(FOCUS_INPUT)
        val specialKey = SpecialKeys.fromInt(sKey)

        when (specialKey) {
            SpecialKeys.DEL -> delete(false)

            SpecialKeys.FORWARD_DEL -> delete(true)

            SpecialKeys.MOVE_END -> moveCursor(forward = true, Movement.LINE)

            SpecialKeys.MOVE_HOME -> moveCursor(forward = false, Movement.LINE)

            SpecialKeys.DPAD_UP -> moveCursor(forward = false, Movement.LINE)

            SpecialKeys.DPAD_DOWN -> moveCursor(forward = true, Movement.LINE)

            SpecialKeys.DPAD_LEFT -> moveCursor(false)

            SpecialKeys.DPAD_RIGHT -> moveCursor(true)


            SpecialKeys.ENTER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && focus?.isMultiLine == false) {
                    focus.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    return
                }
                keyInput("\n")
            }

            SpecialKeys.ESCAPE, SpecialKeys.TAB -> {
                focus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
            }

            else -> {}
        }
    }

    private fun keyInput(key: String) {
        val focus = RemoteKeyboardInputService.window?.findFocus(FOCUS_INPUT)
            ?: return
        val text = getFieldText(focus) ?: ""
        val arguments = Bundle()
        val selectionStart = focus.textSelectionStart
        val selectionEnd = focus.textSelectionEnd

        val newTextPartOne = text.substring(0, selectionStart.coerceIn(0, text.length))
        val newTextPartTwo = text.substring(selectionEnd.coerceIn(0, text.length))
        val newText = newTextPartOne + key + newTextPartTwo

        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            newText
        )

        focus.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )

        if (text == "") {
            return
        }

        moveCursorToPos(selectionStart + key.length, focus)
    }

    private fun delete(forward: Boolean, words: Boolean = false) {
        val focus = RemoteKeyboardInputService.window?.findFocus(FOCUS_INPUT)
            ?: return
        val text = getFieldText(focus)
            ?: return
        val selectionStart = focus.textSelectionStart
        val selectionEnd = focus.textSelectionEnd
        val selectionFirst = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val selectionLast = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val hasSelection = selectionFirst != selectionLast

        var dropAmount =
            if (hasSelection) {
                0
            } else if (words && forward) {
                text.indexOf(' ', selectionFirst + 1, true) - selectionFirst
            } else if (words) {
                if (selectionFirst == 0) {
                    0
                } else {
                    selectionFirst - text.substring(0, selectionFirst - 1)
                        .indexOfLast { c -> c.isWhitespace() } - 1
                }
            } else {
                1
            }

        // indexOf returns -1 if no preceding space exists, which correctly
        // causes dropAmount to equal selectionStart (delete to beginning of field)
        if (dropAmount < 0 && forward) {
            dropAmount = text.length - selectionFirst

        }

        val newTextPartOne = text.substring(0, selectionFirst)
        val newTextPartTwo = text.substring(selectionLast, text.length)
        val newText =
            if (forward) {
                newTextPartOne + newTextPartTwo.drop(dropAmount)
            } else {
                newTextPartOne.dropLast(dropAmount) + newTextPartTwo
            }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
        }

        focus.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )

        val moveCursorBy =
            if (forward) {
                0
            } else {
                -dropAmount
            }

        if (hasSelection) {
            moveCursorToPos(selectionFirst, focus)
        } else {
            moveCursorToPos(selectionFirst + moveCursorBy, focus)
        }
    }

    private fun moveCursorToPos(
        pos: Int,
        focus: AccessibilityNodeInfo
    ) {
        val pos = if (pos < 0) 0 else pos
        focus.setSelection(pos, pos)
    }

    private fun AccessibilityNodeInfo.setSelection(
        start: Int,
        end: Int,
    ) {
        this.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        })
    }

    private fun moveCursor(
        forward: Boolean,
        movement: Movement = Movement.CHARACTER,
        makeSelection: Boolean = false
    ) {
        val focus = RemoteKeyboardInputService.window?.findFocus(FOCUS_INPUT)
        val args = Bundle().apply {
            putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                when (movement) {
                    Movement.WORD -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                    Movement.LINE -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                    else -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                }
            )
            putBoolean(
                AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                makeSelection
            )
        }

        focus?.performAction(
            if (forward) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY.id
            } else {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY.id
            },
            args
        )
    }

    private fun getFieldText(focus: AccessibilityNodeInfo): String? {
        if (focus.isShowingHintText) return null
        // Fallback: some apps return hint as real text but report selection -1,-1
        if (focus.textSelectionStart == -1 && focus.textSelectionEnd == -1) return null
        return focus.text?.toString()
    }

    fun sendState() {
        if (isDeviceInitialized) {
            val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
            np.set("state", value = checkRequiredPermissions())
            device.sendPacket(np)
        } else {
            Log.d(LOG_TAG, "Not initialized")
        }
    }

}
