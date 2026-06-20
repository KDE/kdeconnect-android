/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.DeviceStats
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.PluginPreference.PluginPreferenceCallback
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityPluginSettingsBinding
import java.util.Objects

class PluginSettingsActivity : BaseActivity<ActivityPluginSettingsBinding>(), PluginPreferenceCallback {

    override val binding by lazy { ActivityPluginSettingsBinding.inflate(layoutInflater) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        var pluginKey: String? = null

        if (intent.hasExtra(EXTRA_DEVICE_ID)) {
            settingsDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
            if (intent.hasExtra(EXTRA_PLUGIN_KEY)) {
                pluginKey = intent.getStringExtra(EXTRA_PLUGIN_KEY)
            }
        } else if (settingsDeviceId == null) {
            throw RuntimeException("You must start DeviceSettingActivity using an intent that has a $EXTRA_DEVICE_ID extra")
        }

        var fragment = supportFragmentManager.findFragmentById(R.id.fragmentPlaceHolder)
        if (fragment == null) {
            if (pluginKey != null) {
                val device = getInstance().getDevice(settingsDeviceId)
                if (device != null) {
                    val plugin = device.getPluginIncludingWithoutPermissions(pluginKey)
                    if (plugin != null) {
                        fragment = plugin.getSettingsFragment(this)
                    }
                }
            }
            if (fragment == null) {
                fragment = PluginSettingsListFragment.newInstance(settingsDeviceId!!)
            }

            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragmentPlaceHolder, fragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val fm = supportFragmentManager

            if (fm.backStackEntryCount > 0) {
                fm.popBackStack()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            return false // PacketStats not working in API < 24
        }
        menu.add(R.string.plugin_stats)
            .setOnMenuItemClickListener {
                val stats = DeviceStats.getStatsForDevice(settingsDeviceId!!)
                val alertDialog = MaterialAlertDialogBuilder(this@PluginSettingsActivity)
                    .setTitle(R.string.plugin_stats)
                    .setPositiveButton(R.string.ok) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setMessage(stats)
                    .show()
                val messageView = alertDialog.findViewById<View?>(android.R.id.message)
                if (messageView is TextView) {
                    messageView.setTextIsSelectable(true)
                }
                true
            }
        return true
    }

    override fun onStartPluginSettingsFragment(plugin: Plugin) {
        setTitle(getString(R.string.plugin_settings_with_name, plugin.displayName))

        // TODO: getSettingsFragment return is nullable because NotificationFilterActivity isn't a PluginSettingsFragment yet
        val fragment = plugin.getSettingsFragment(this)
            ?: return

        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentPlaceHolder, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressed()
        return true
    }

    override fun onFinish() {
        finish()
    }

    companion object {
        const val EXTRA_DEVICE_ID: String = "deviceId"
        const val EXTRA_PLUGIN_KEY: String = "pluginKey"

        // Weird name because Activity already has getters and setters for 'deviceId'
        // Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.
        var settingsDeviceId: String? = null
            private set
    }
}
