/*
 * SPDX-FileCopyrightText: 2017 Holger Kaelberer <holger.k@elberer.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.remotekeyboardime

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.SparseIntArray
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R
import java.util.ArrayList
import java.util.concurrent.locks.ReentrantLock

@PluginFactory.LoadablePlugin
class RemoteKeyboardIMEPlugin : Plugin(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate() {
        Log.d(LOG_TAG, "Creating for device ${device.name}")
        acquireInstances()
        try {
            instances.add(this)
        } finally {
            releaseInstances()
        }
        RemoteKeyboardService.instance?.let { service ->
            service.handler.post { service.updateInputView() }
        }

        preferences.registerOnSharedPreferenceChangeListener(this)

        notifyKeyboardState(isKeyboardAvailable)
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        notifyKeyboardState(false)
        acquireInstances()
        try {
            if (instances.contains(this)) {
                instances.remove(this)
                if (instances.isEmpty()) {
                    RemoteKeyboardService.instance?.let { service ->
                        service.handler.post { service.updateInputView() }
                    }
                }
            }
        } finally {
            releaseInstances()
        }

        Log.d(LOG_TAG, "Destroying for device ${device.name}")
    }

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_remotekeyboard_ime)

    override val description: String
        get() = context.getString(R.string.pref_plugin_remotekeyboard_desc)

    override fun hasSettings(): Boolean = true

    override val settingsScope = SettingsScope.APP_WIDE

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment {
        return PluginSettingsFragment.newInstance(pluginKey, device.deviceId, R.xml.remotekeyboardplugin_preferences)
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_ECHO, PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)

    private fun isValidSpecialKey(key: Int): Boolean {
        return specialKeyMap.get(key, 0) > 0
    }

    private fun handleSpecialKey(key: Int, shift: Boolean, ctrl: Boolean, alt: Boolean): Boolean {
        val keyEvent = specialKeyMap.get(key, 0)
        if (keyEvent == 0) {
            return false
        }
        val inputConn: InputConnection = RemoteKeyboardService.instance?.currentInputConnection ?: return false
        //Log.d(LOG_TAG, "Handling special key $key translated to $keyEvent shift=$shift ctrl=$ctrl alt=$alt");

        // special sequences:
        if (ctrl && (keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT || keyEvent == KeyEvent.KEYCODE_DPAD_LEFT)) {
            // Ctrl + right/left -> jump to next/previous word
            val forward = keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT
            val extractedText = inputConn.getExtractedText(ExtractedTextRequest(), 0) ?: return false
            val text = extractedText.text?.toString() ?: return false
            val cursor = extractedText.selectionEnd
            val spacePos = if (forward) {
                text.indexOf(' ', cursor)
            } else {
                text.lastIndexOf(' ', cursor - 2)
            }
            val pos = when {
                spacePos != -1 -> spacePos + 1
                forward -> text.length
                else -> 0
            }
            // Shift -> extend/shrink the selection, keeping its anchor
            val anchor = if (shift) extractedText.selectionStart else pos
            inputConn.setSelection(anchor, pos)
        } else if (shift && (keyEvent == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyEvent == KeyEvent.KEYCODE_DPAD_UP ||
                keyEvent == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyEvent == KeyEvent.KEYCODE_MOVE_HOME ||
                keyEvent == KeyEvent.KEYCODE_MOVE_END)) {
            // Shift + up/down/left/right/home/end
            val now = SystemClock.uptimeMillis()
            inputConn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
            inputConn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyEvent, 0, KeyEvent.META_SHIFT_LEFT_ON))
            inputConn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyEvent, 0, KeyEvent.META_SHIFT_LEFT_ON))
            inputConn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
        } else if (keyEvent == KeyEvent.KEYCODE_NUMPAD_ENTER || keyEvent == KeyEvent.KEYCODE_ENTER) {
            // Enter key
            val editorInfo: EditorInfo? = RemoteKeyboardService.instance?.currentInputEditorInfo
            if (editorInfo != null && (((editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) || ctrl)) { // Ctrl+Return overrides IME_FLAG_NO_ENTER_ACTION (FIXME: make configurable?)
                // check for special DONE/GO/etc actions first:
                val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                return when (action) {
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_SEARCH -> {
                        inputConn.performEditorAction(action)
                        true
                    }
                    else -> false
                }
            } else {
                // else: fall back to regular Enter-event:
//                Log.d(LOG_TAG, "Enter: normal keypress");
                inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
                inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEvent))
            }
        } else {
            // default handling:
            inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
            inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEvent))
        }

        return true
    }

    private fun handleVisibleKey(key: String, shift: Boolean, ctrl: Boolean, alt: Boolean): Boolean {
        //Log.d(LOG_TAG, "Handling visible key $key shift=$shift ctrl=$ctrl alt=$alt ${key.equals("c", true)} ${key.length}");

        if (key.isEmpty()) {
            return false
        }

        val inputConn = RemoteKeyboardService.instance?.currentInputConnection
            ?: return false

        // ctrl+c/v/x
        if (key.equals("c", ignoreCase = true) && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.copy)
        } else if (key.equals("v", ignoreCase = true) && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.paste)
        } else if (key.equals("x", ignoreCase = true) && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.cut)
        } else if (key.equals("a", ignoreCase = true) && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.selectAll)
        }

        //Log.d(LOG_TAG, "Committing visible key '$key'");
        inputConn.commitText(key, key.length)
        return true
    }

    private fun handleEvent(np: NetworkPacket): Boolean {
        if (np.has("specialKey") && isValidSpecialKey(np.getInt("specialKey"))) {
            return handleSpecialKey(
                np.getInt("specialKey"),
                np.getBoolean("shift"),
                np.getBoolean("ctrl"),
                np.getBoolean("alt"),
            )
        }

        // try visible key
        return handleVisibleKey(
            np.getString("key"),
            np.getBoolean("shift"),
            np.getBoolean("ctrl"),
            np.getBoolean("alt"),
        )
    }

    enum class MousePadPacketType {
        Keyboard,
        Mouse,
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (getMousePadPacketType(np) != MousePadPacketType.Keyboard) {
            return false // This packet will be handled by the MouseReceiverPlugin instead, silently ignore
        }

        val service = RemoteKeyboardService.instance ?: return false // This packet will be handled by the RemoteKeyboardPlugin instead, silently ignore

        if (!service.visible && preferences.getBoolean(context.getString(R.string.remotekeyboard_editing_only), true)) {
            Log.i(LOG_TAG, "Remote keyboard is currently not visible, dropping key")
            return false
        }

        if (!handleEvent(np)) {
            Log.i(LOG_TAG, "Could not handle event!")
            return false
        }

        if (np.getBoolean("sendAck")) {
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
            reply["isAck"] = true
            device.sendPacket(reply)
        }

        return true
    }

    fun notifyKeyboardState(state: Boolean) {
        if (!state) {
            val accessibilityKeyboardPlugin = device.getPlugin(RemoteKeyboardPlugin::class.java)
            if (accessibilityKeyboardPlugin != null) {
                // Do not report false state if accessibility keyboard is active
                return
            }
        }
        Log.d(LOG_TAG, "Keyboardstate changed to $state")
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
        np["state"] = state
        device.sendPacket(np)
    }

    val isKeyboardAvailable: Boolean
        get() {
            val editingOnly = preferences.getBoolean(context.getString(R.string.remotekeyboard_editing_only), true)
            val visible = RemoteKeyboardService.instance?.visible == true
            return !editingOnly || visible
        }

    val deviceId: String
        get() = device.deviceId

    override fun checkRequiredPermissions(): Boolean {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethodList: List<InputMethodInfo> = inputMethodManager.enabledInputMethodList
        return inputMethodList.any { info ->
            context.packageName == info.packageName
        }
    }

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_remotekeyboard_desc)
            .setMessage(R.string.no_permissions_remotekeyboard_ime)
            .setPositiveButton(R.string.open_input_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
            .setReloadPluginsOnResult(true)
            .create()

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == context.getString(R.string.remotekeyboard_editing_only)) {
            notifyKeyboardState(isKeyboardAvailable)
        }
    }

    companion object {
        private const val LOG_TAG = "RemoteKeyboardIMEPlugin"

        private const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
        private const val PACKET_TYPE_MOUSEPAD_ECHO = "kdeconnect.mousepad.echo"
        private const val PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate"

        /**
         * Track and expose plugin instances to allow for a 'connected'-indicator in the IME:
         */
        private val instances = ArrayList<RemoteKeyboardIMEPlugin>()
        private val instancesLock = ReentrantLock(true)

        fun acquireInstances(): ArrayList<RemoteKeyboardIMEPlugin> {
            instancesLock.lock()
            return instances
        }

        fun releaseInstances(): ArrayList<RemoteKeyboardIMEPlugin> {
            instancesLock.unlock()
            return instances
        }

        fun isConnected(): Boolean {
            return instances.isNotEmpty()
        }

        fun getMousePadPacketType(np: NetworkPacket): MousePadPacketType {
            return if (np.has("key") || np.has("specialKey")) {
                MousePadPacketType.Keyboard
            } else {
                MousePadPacketType.Mouse
            }
        }

        private val specialKeyMap = SparseIntArray().apply {
            var i = 0
            put(++i, KeyEvent.KEYCODE_DEL) // 1
            put(++i, KeyEvent.KEYCODE_TAB) // 2
            ++i // 3
            put(++i, KeyEvent.KEYCODE_DPAD_LEFT) // 4
            put(++i, KeyEvent.KEYCODE_DPAD_UP) // 5
            put(++i, KeyEvent.KEYCODE_DPAD_RIGHT) // 6
            put(++i, KeyEvent.KEYCODE_DPAD_DOWN) // 7
            put(++i, KeyEvent.KEYCODE_PAGE_UP) // 8
            put(++i, KeyEvent.KEYCODE_PAGE_DOWN) // 9
            put(++i, KeyEvent.KEYCODE_MOVE_HOME) // 10
            put(++i, KeyEvent.KEYCODE_MOVE_END) // 11
            put(++i, KeyEvent.KEYCODE_ENTER) // 12
            put(++i, KeyEvent.KEYCODE_FORWARD_DEL) // 13
            put(++i, KeyEvent.KEYCODE_ESCAPE) // 14
            put(++i, KeyEvent.KEYCODE_SYSRQ) // 15
            put(++i, KeyEvent.KEYCODE_SCROLL_LOCK) // 16
            ++i // 17
            ++i // 18
            ++i // 19
            ++i // 20
            put(++i, KeyEvent.KEYCODE_F1) // 21
            put(++i, KeyEvent.KEYCODE_F2) // 22
            put(++i, KeyEvent.KEYCODE_F3) // 23
            put(++i, KeyEvent.KEYCODE_F4) // 24
            put(++i, KeyEvent.KEYCODE_F5) // 25
            put(++i, KeyEvent.KEYCODE_F6) // 26
            put(++i, KeyEvent.KEYCODE_F7) // 27
            put(++i, KeyEvent.KEYCODE_F8) // 28
            put(++i, KeyEvent.KEYCODE_F9) // 29
            put(++i, KeyEvent.KEYCODE_F10) // 30
            put(++i, KeyEvent.KEYCODE_F11) // 31
            put(++i, KeyEvent.KEYCODE_F12) // 32
        }
    }
}
