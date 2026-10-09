/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.runcommand

import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R

class RunCommandUrlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != null) {
            try {
                val uri = intent.data ?: return
                val deviceId = uri.pathSegments[0]

                val device = KdeConnect.getInstance().getDevice(deviceId)

                if (device == null) {
                    error(R.string.runcommand_nosuchdevice)
                    return
                }

                if (!device.isPaired) {
                    error(R.string.runcommand_notpaired)
                    return
                }

                if (!device.isReachable) {
                    error(R.string.runcommand_notreachable)
                    return
                }

                val plugin = device.getPlugin(RunCommandPlugin::class.java)
                if (plugin == null) {
                    error(R.string.runcommand_noruncommandplugin)
                    return
                }

                plugin.runCommand(uri.pathSegments[1])
                finish()

                val vibrator = getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            } catch (e: Exception) {
                Log.e("RuncommandPlugin", "Exception", e)
            }
        }
    }

    private fun error(@StringRes message: Int) {
        val view = TextView(this).apply {
            setText(message)
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
