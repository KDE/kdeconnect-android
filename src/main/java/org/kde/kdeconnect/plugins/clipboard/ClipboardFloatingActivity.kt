/*
 * SPDX-FileCopyrightText: 2020 Anjani Kumar <anjanik012@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.clipboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect_tp.R

/*
    An activity to access the clipboard on Android 10 and later by raising over other apps.
    This is invisible and doesn't require any interaction from the user.
    This should be called when a change in clipboard is detected. This can be done by manually
    when user wants to send the clipboard or by reading system log files which requires a special
    privileged permission android.permission.READ_LOGS.
    https://developer.android.com/reference/android/Manifest.permission#READ_LOGS
    This permission can be gained by only from the adb by the user.
    https://www.reddit.com/r/AndroidBusters/comments/fh60lt/how_to_solve_a_problem_with_the_clipboard_on/
    Like:
    # Enable the READ_LOGS permission. There is no other way to do this for a regular user app.
    adb -d shell pm grant org.kde.kdeconnect_tp android.permission.READ_LOGS;
    # Allow "Drawing over other apps", also accessible from Settings on the phone.
    # Optional, but makes the feature much more reliable.
    adb -d shell appops set org.kde.kdeconnect_tp SYSTEM_ALERT_WINDOW allow;
    # Kill the app, new permissions take effect on restart.
    adb -d shell am force-stop org.kde.kdeconnect_tp;

    Currently this activity is being triggered from a button in Foreground Notification or quick settings tile.
 */
class ClipboardFloatingActivity : AppCompatActivity() {

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // We are now sure that clipboard can be accessed from here.
            ClipboardListener.instance(this).onClipboardChanged()
            if (shouldShowToast()) {
                Toast.makeText(this, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard_floating)
        val wlp = window.attributes
        wlp.dimAmount = 0f
        wlp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        window.attributes = wlp
    }

    private fun shouldShowToast(): Boolean {
        return intent.getBooleanExtra(KEY_SHOW_TOAST, false)
    }

    companion object {
        private const val KEY_SHOW_TOAST = "SHOW_TOAST"

        @JvmStatic
        fun getIntent(context: Context, showToast: Boolean): Intent {
            return Intent(context.applicationContext, ClipboardFloatingActivity::class.java).apply {
                putExtra(KEY_SHOW_TOAST, showToast)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
