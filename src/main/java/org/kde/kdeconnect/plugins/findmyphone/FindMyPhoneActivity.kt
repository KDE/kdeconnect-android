/* SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.findmyphone

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect_tp.databinding.ActivityFindMyPhoneBinding

class FindMyPhoneActivity : BaseActivity<ActivityFindMyPhoneBinding>() {
    private var deviceId: String? = null

    override val binding: ActivityFindMyPhoneBinding by lazy {
        ActivityFindMyPhoneBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if (!intent.hasExtra(EXTRA_DEVICE_ID)) {
            Log.e(TAG, "You must include the deviceId for which this activity is started as an intent EXTRA")
            finish()
            return
        }

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.bFindMyPhone.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        val plugin = deviceId?.let { KdeConnect.getInstance().getDevicePlugin(it, FindMyPhonePlugin::class.java) } ?: return
        plugin.startPlaying()
        plugin.startFlashing()
        plugin.hideNotification()
    }

    override fun onStop() {
        super.onStop()
        val plugin = deviceId?.let { KdeConnect.getInstance().getDevicePlugin(it, FindMyPhonePlugin::class.java) } ?: return
        plugin.stopPlaying()
        plugin.stopFlashing()
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
        private const val TAG = "FindMyPhoneActivity"
    }
}
