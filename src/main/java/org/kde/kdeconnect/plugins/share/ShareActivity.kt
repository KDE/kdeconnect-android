/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.helpers.WindowHelper
import org.kde.kdeconnect.ui.list.DeviceItem
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect.ui.list.SectionItem
import org.kde.kdeconnect.ui.list.UnreachableDeviceItem
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding

class ShareActivity : BaseActivity<ActivityShareBinding>() {

    private lateinit var mSharedPrefs: SharedPreferences

    override val binding: ActivityShareBinding by lazy { ActivityShareBinding.inflate(layoutInflater) }

    override val isScrollable: Boolean = true

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
        BackgroundService.ForceRefreshConnections(context = this)

        binding.devicesListLayout.refreshListLayout.isRefreshing = true
        binding.devicesListLayout.refreshListLayout.postDelayed({
            binding.devicesListLayout.refreshListLayout.isRefreshing = false
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
        val devicesList = mutableListOf<Device>()
        val items = mutableListOf<ListAdapter.Item>()

        val intentHasUrl = doesIntentContainUrl(intent)

        var sectionString = getString(R.string.share_to)
        if (intentHasUrl) {
            sectionString =
                getString(R.string.unreachable_device_url_share_text) + getString(R.string.share_to)
        }
        val section = SectionItem(title = sectionString)
        items.add(section)

        for (device in devices) {
            // Show the paired devices only if they are unreachable and the shared intent has a URL
            if (device.isPaired && (intentHasUrl || device.isReachable)) {
                devicesList.add(device)
                if (!device.isReachable) {
                    items.add(
                        UnreachableDeviceItem(device = device) {
                            deviceClicked(
                                device = device,
                                intentHasUrl = intentHasUrl,
                                intent = intent
                            )
                        }
                    )
                } else {
                    items.add(
                        DeviceItem(device = device) {
                            deviceClicked(
                                device = device,
                                intentHasUrl = intentHasUrl,
                                intent = intent
                            )
                        }
                    )
                }
                section.isEmpty = false
            }
        }

        binding.devicesListLayout.devicesList.adapter = ListAdapter(
            context = this,
            items
        )

        // Configure focus order for Accessibility, for touchpads, and for TV remotes
        // (allow focus of items in the device list)
        binding.devicesListLayout.devicesList.itemsCanFocus = true
    }

    private fun deviceClicked(
        device: Device,
        intentHasUrl: Boolean,
        intent: Intent
    ) {
        val plugin: SharePlugin? =
            KdeConnect.getInstance().getDevicePlugin(
                deviceId = device.deviceId,
                pluginClass = SharePlugin::class.java
            )
        if (intentHasUrl && !device.isReachable) {
            // Store the URL to be delivered once device becomes online
            storeUrlForFutureDelivery(
                device = device,
                url = intent.getStringExtra(Intent.EXTRA_TEXT)
            )
        } else {
            plugin?.share(intent)
        }
        finish()
    }

    private fun doesIntentContainUrl(intent: Intent?): Boolean {
        intent?.extras?.let { extras ->
            val url = extras.getString(Intent.EXTRA_TEXT)
            return URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)
        }
        return false
    }

    private fun storeUrlForFutureDelivery(
        device: Device,
        url: String?
    ) {
        val key = KEY_UNREACHABLE_URL_LIST + device.deviceId
        val oldUrlSet = mSharedPrefs.getStringSet(key, null)
        // According to the API docs, we should not directly modify the set returned above
        val newUrlSet = mutableSetOf<String>()
        url?.let { urlSet -> newUrlSet.add(urlSet) }
        if (oldUrlSet != null) {
            newUrlSet.addAll(oldUrlSet)
        }

        mSharedPrefs.edit().putStringSet(key, newUrlSet).apply()
        Toast.makeText(this, getString(R.string.unreachable_share_toast), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            displayOptions =
                ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_SHOW_CUSTOM
        }

        binding.devicesListLayout.refreshListLayout.setOnRefreshListener { refreshDevicesAction() }

        WindowHelper.setupBottomPadding(binding.devicesListLayout.devicesList)
    }

    override fun onStart() {
        super.onStart()

        val intent = intent
        var deviceId = intent.getStringExtra("deviceId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        }

        if (deviceId != null) {
            val plugin: SharePlugin? =
                KdeConnect.getInstance().getDevicePlugin(deviceId, SharePlugin::class.java)
            if (plugin != null) {
                plugin.share(intent)
            } else {
                val extras = intent.extras
                if (extras != null && extras.containsKey(Intent.EXTRA_TEXT)) {
                    val device = KdeConnect.getInstance().getDevice(id = deviceId)
                    if (doesIntentContainUrl(intent) && device != null && !device.isReachable) {
                        val text = extras.getString(Intent.EXTRA_TEXT)
                        storeUrlForFutureDelivery(
                            device = device,
                            url = text
                        )
                    }
                }
            }
            finish()
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