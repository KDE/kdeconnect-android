/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mousepad

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.helpers.SafeTextChecker
import org.kde.kdeconnect.helpers.WindowHelper
import org.kde.kdeconnect.ui.list.DeviceItem
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect.ui.list.SectionItem
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivitySendkeystrokesBinding

class SendKeystrokesToHostActivity : BaseActivity<ActivitySendkeystrokesBinding>() {

    private var contentIsOkay: Boolean = false

    private val lazyBinding = lazy { ActivitySendkeystrokesBinding.inflate(layoutInflater) }

    override val binding: ActivitySendkeystrokesBinding
        get() = lazyBinding.value

    override val isScrollable: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        WindowHelper.setupBottomPadding(binding.devicesList)
    }

    override fun onStart() {
        super.onStart()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(getString(R.string.pref_sendkeystrokes_enabled), true)) {
            Toast.makeText(applicationContext, R.string.sendkeystrokes_disabled_toast, Toast.LENGTH_LONG).show()
            finish()
        } else {
            val intent = intent
            val type = intent.type

            if ("text/x-keystrokes" == type) {
                val toSend = intent.getStringExtra(Intent.EXTRA_TEXT)
                binding.textToSend.setText(toSend)

                // if the preference send_safe_text_immediately is true, we will check if exactly one
                // device is connected and send the text to it without user confirmation, to make sending of
                // short and safe text like PINs/TANs very fluent
                //
                // (contentIsOkay gets used in updateDeviceList again)
                if (prefs.getBoolean(getString(R.string.pref_send_safe_text_immediately), true)) {
                    val safeTextChecker = SafeTextChecker(SAFE_CHARS, MAX_SAFE_LENGTH)
                    contentIsOkay = safeTextChecker.isSafe(toSend)
                } else {
                    contentIsOkay = false
                }

                // If we trust the sending app, check if there is only one device paired / reachable...
                if (contentIsOkay) {
                    val reachableDevices = KdeConnect.getInstance().devices.values
                        .filter { it.isReachable }
                        .take(2) // we only need the first two; if its more than one, we need to show the user the device-selection

                    // if its exactly one just send the text to it
                    if (reachableDevices.size == 1) {
                        // send the text and close this activity
                        sendKeys(reachableDevices[0])
                        finish()
                        return
                    }
                }

                KdeConnect.getInstance().addDeviceListChangedCallback("SendKeystrokesToHostActivity") {
                    runOnUiThread { updateDeviceList() }
                }
                BackgroundService.ForceRefreshConnections(this) // force a network re-discover
                updateDeviceList()
            } else {
                Toast.makeText(applicationContext, R.string.sendkeystrokes_wrong_data, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("SendKeystrokesToHostActivity")
        super.onStop()
    }

    private fun sendKeys(deviceId: Device) {
        val text = binding.textToSend.text
        val toSend = text?.toString()?.trim() ?: ""
        if (toSend.isNotEmpty()) {
            val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId.deviceId, MousePadPlugin::class.java)
            if (plugin == null) {
                finish()
                return
            }
            plugin.sendText(toSend)
            Toast.makeText(
                applicationContext,
                getString(R.string.sendkeystrokes_sent_text, toSend, deviceId.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateDeviceList() {
        val devices = KdeConnect.getInstance().devices.values
        val devicesList = ArrayList<Device>()
        val items = ArrayList<ListAdapter.Item>()

        val section = SectionItem(getString(R.string.sendkeystrokes_send_to))
        items.add(section)

        for (d in devices) {
            if (d.isReachable && d.isPaired) {
                devicesList.add(d)
                items.add(DeviceItem(d, ::deviceClicked))
                section.isEmpty = false
            }
        }

        binding.devicesList.adapter = ListAdapter(this@SendKeystrokesToHostActivity, items)

        // Configure focus order for Accessibility, for touchpads, and for TV remotes
        // (allow focus of items in the device list)
        binding.devicesList.itemsCanFocus = true

        // only one device is connected and we trust the text to send -> send it and close the activity.
        // Usually we already check it in `onStart` - but if the BackgroundService was not started/connected to the host
        // it will not have the deviceList in memory. Use this callback as second chance (but it will flicker a bit, because the activity might
        // already been visible and get closed again quickly)
        if (devicesList.size == 1 && contentIsOkay) {
            val device = devicesList[0]
            sendKeys(device)
            finish() // close the activity
        }
    }

    private fun deviceClicked(device: Device) {
        sendKeys(device)
        finish() // close the activity
    }

    companion object {
        // text with these length and content can be send without user confirmation.
        // more or less chosen arbitrarily, so that we allow short PINS and TANS without interruption (if only one device is connected)
        // but also be on the safe side, so that apps cant send any harmful content
        const val MAX_SAFE_LENGTH = 8
        const val SAFE_CHARS = "1234567890"
    }
}
