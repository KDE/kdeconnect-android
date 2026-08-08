/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.compose.screen.share.ShareScreen
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding
import androidx.core.content.edit
import org.kde.kdeconnect.helpers.IntentHelper

class ShareActivity : BaseActivity<ActivityShareBinding>() {

    override val binding: ActivityShareBinding by lazy { ActivityShareBinding.inflate(layoutInflater) }

    override val isScrollable: Boolean = true

    private var isRefreshing by mutableStateOf(value = false)
    private var uiDevices by mutableStateOf<List<DeviceUiModel>>(value = emptyList())
    private var intentHasUrl by mutableStateOf(value = false)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.refresh, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_refresh) {
            refreshDevicesAction()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun refreshDevicesAction() {
        isRefreshing = true

        BackgroundService.ForceRefreshConnections(context = this)

        binding.devicesListLayout.composeView.postDelayed({
            isRefreshing = false
        }, 1500)
    }

    private fun updateDeviceList() {
        val intent = intent
        val action = intent.action
        if (Intent.ACTION_SEND != action && Intent.ACTION_SEND_MULTIPLE != action) {
            finish()
            return
        }
        val devices = KdeConnect.getInstance().devices.values
        this.intentHasUrl = IntentHelper.parseIntentUrls(intent).isNotEmpty()
        this.uiDevices = devices
            .filter { device -> device.isPaired && (intentHasUrl || device.isReachable) }
            .map { it.toUiModel() }
    }

    private fun shareToDeviceAndFinish(
        deviceId: String,
        intent: Intent
    ) {
        val device = KdeConnect.getInstance().getDevice(id = deviceId)
        if (device == null) {
            Toast.makeText(this, getString(R.string.unknown_device), Toast.LENGTH_LONG ).show()
        } else if (!device.isReachable) {
            // Store the URL to be delivered once device becomes online
            val urls = IntentHelper.parseIntentUrls(intent)
            if (urls.isNotEmpty()) {
                storeUrlForFutureDelivery(device, urls.toSet())
            } else {
                Toast.makeText(this, getString(R.string.error_not_reachable), Toast.LENGTH_LONG).show()
            }
        } else {
            val plugin = device.getPlugin(SharePlugin::class.java)
            plugin?.share(intent)
        }
        finish()
    }

    private fun storeUrlForFutureDelivery(
        device: Device,
        urls: Set<String>
    ) {
        val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = KEY_UNREACHABLE_URL_LIST + device.deviceId
        val existingUrls = mSharedPrefs.getStringSet(key, null) ?: emptySet()
        mSharedPrefs.edit { putStringSet(key, urls + existingUrls) }
        Toast.makeText(this, getString(R.string.unreachable_share_toast), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            displayOptions =
                ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_SHOW_CUSTOM
        }

        binding.devicesListLayout.composeView.setContent {
            KdeTheme(this) {
                ShareScreen(
                    devices = uiDevices,
                    intentHasUrl = intentHasUrl,
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        shareToDeviceAndFinish(
                            deviceId = deviceId,
                            intent = intent
                        )
                    },
                    onRefresh = { refreshDevicesAction() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val intent = intent
        var deviceId = intent.getStringExtra("deviceId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        }

        if (deviceId != null) {
            shareToDeviceAndFinish(deviceId, intent)
        } else {
            KdeConnect.getInstance().addDeviceListChangedCallback(key = "ShareActivity") {
                runOnUiThread { updateDeviceList() }
            }
            BackgroundService.ForceRefreshConnections(context = this) // force a network re-discover
            updateDeviceList()
        }
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback(key = "ShareActivity")
        super.onStop()
    }

    companion object {
        private const val KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list"
    }
}