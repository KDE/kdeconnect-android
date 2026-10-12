/*
 * SPDX-FileCopyrightText: 2017 Holger Kaelberer <holger.k@elberer.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.remotekeyboardime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PluginSettingsActivity
import org.kde.kdeconnect_tp.R

open class RemoteKeyboardService :
    InputMethodService(),
    OnKeyboardActionListener {

    /**
     * Whether this InputMethod is currently visible.
     */
    @VisibleForTesting
    var visible: Boolean = false

    private var inputView: KeyboardView? = null

    val handler: Handler = Handler(Looper.getMainLooper())

    fun updateInputView() {
        val view = inputView ?: return
        val currentKeyboard = view.keyboard ?: return
        val keys = currentKeyboard.keys ?: return
        val connected = RemoteKeyboardIMEPlugin.isConnected()
//        Log.d("RemoteKeyboardService", "Updating keyboard connection icon, connected=" + connected);
        val disconnectedIcon = R.drawable.ic_phonelink_off_36dp
        val connectedIcon = R.drawable.ic_phonelink_36dp
        val statusKeyIdx = 3
        if (statusKeyIdx < keys.size) {
            keys[statusKeyIdx].icon = ContextCompat.getDrawable(this, if (connected) connectedIcon else disconnectedIcon)
            view.invalidateKey(statusKeyIdx)
        }
    }

    override fun onCreate() {
        super.onCreate()
        visible = false
        instance = this
        Log.d("RemoteKeyboardService", "Remote keyboard initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        visible = false
        instance = null
        Log.d("RemoteKeyboardService", "Destroyed")
    }

    override fun onCreateInputView(): View {
//        Log.d("RemoteKeyboardService", "onCreateInputView connected=" + RemoteKeyboardPlugin.isConnected());
        val view = KeyboardView(this, null).apply {
            keyboard = Keyboard(this@RemoteKeyboardService, R.xml.remotekeyboardplugin_keyboard)
            isPreviewEnabled = false
            setOnKeyboardActionListener(this@RemoteKeyboardService)
        }
        inputView = view
        updateInputView()
        return view
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
//        Log.d("RemoteKeyboardService", "onStartInputView");
        super.onStartInputView(attribute, restarting)
        visible = true
        val instances = RemoteKeyboardIMEPlugin.acquireInstances()
        try {
            for (i in instances) {
                i.notifyKeyboardState(true)
            }
        } finally {
            RemoteKeyboardIMEPlugin.releaseInstances()
        }

        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
//        Log.d("RemoteKeyboardService", "onFinishInputView");
        super.onFinishInputView(finishingInput)
        visible = false
        val instances = RemoteKeyboardIMEPlugin.acquireInstances()
        try {
            for (i in instances) {
                i.notifyKeyboardState(false)
            }
        } finally {
            RemoteKeyboardIMEPlugin.releaseInstances()
        }

        window?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPress(primaryCode: Int) {
        when (primaryCode) {
            0 -> { // "hide keyboard"
                requestHideSelf(0)
            }
            1 -> { // "settings"
                val instances = RemoteKeyboardIMEPlugin.acquireInstances()
                try {
                    if (instances.size == 1) { // single instance of RemoteKeyboardPlugin -> access its settings
                        val plugin = instances[0]
                        val intent = PluginSettingsActivity.createIntent(this, plugin.deviceId, plugin.pluginKey).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    } else { // != 1 instance of plugin -> show main activity view
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.FLAG_FORCE_OVERVIEW, true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        if (instances.isEmpty()) {
                            Toast.makeText(this, R.string.remotekeyboard_not_connected, Toast.LENGTH_SHORT).show()
                        } else { // instances.size > 1
                            Toast.makeText(this, R.string.remotekeyboard_multiple_connections, Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    RemoteKeyboardIMEPlugin.releaseInstances()
                }
            }
            2 -> { // "keyboard"
                val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
                imm?.showInputMethodPicker()
            }
            3 -> { // "connected"?
                if (RemoteKeyboardIMEPlugin.isConnected()) {
                    Toast.makeText(this, R.string.remotekeyboard_connected, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.remotekeyboard_not_connected, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) { }

    override fun onText(text: CharSequence?) { }

    override fun swipeRight() { }

    override fun swipeLeft() { }

    override fun swipeDown() { }

    override fun swipeUp() { }

    override fun onRelease(primaryCode: Int) { }

    companion object {
        /**
         * Reference to our instance
         * null if this InputMethod is not currently selected.
         */
        var instance: RemoteKeyboardService? = null
    }
}
