/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R
import rikka.shizuku.Shizuku

class BackgroundFeaturesActivity : AppCompatActivity() {
    private var shizukuState by mutableStateOf(ShizukuState(false, false, false))
    private val receivedListener = Shizuku.OnBinderReceivedListener { runOnUiThread(::refresh) }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread(::refresh) }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
        setContent {
            KdeTheme(this) {
                ShizukuSetupScreen(shizukuState, onBack = { finish() }, onCheck = ::refresh,
                    onOpenShizuku = ::openShizuku,
                    onOpenDebugging = { openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                    onRequestPermission = ::requestPermission)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun refresh() {
        shizukuState = ShizukuSupport.state(this)
    }

    private fun requestPermission() {
        if (!ShizukuSupport.state(this).running) { refresh(); return }
        runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) openShizuku()
            else Shizuku.requestPermission(1)
        }.onFailure { refresh() }
    }

    private fun openShizuku() {
        openSettings(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri()))
    }

    internal fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.background_cannot_open, Toast.LENGTH_LONG).show()
        }
    }
}
