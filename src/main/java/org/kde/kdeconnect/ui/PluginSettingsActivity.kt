/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.DeviceStats
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.PluginPreference.PluginPreferenceCallback
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityPluginSettingsBinding

class PluginSettingsActivity : BaseActivity<ActivityPluginSettingsBinding>(), PluginPreferenceCallback {

    override val binding by lazy { ActivityPluginSettingsBinding.inflate(layoutInflater) }

    private lateinit var settingsDeviceId: String

    public override fun onCreate(savedInstanceState: Bundle?) {
        settingsDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
            ?: throw RuntimeException("You must start DeviceSettingActivity using an intent that has a $EXTRA_DEVICE_ID extra")

        super.onCreate(savedInstanceState)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        var fragment = supportFragmentManager.findFragmentById(R.id.fragmentPlaceHolder)
        if (fragment == null) {
            val pluginKey = intent.getStringExtra(EXTRA_PLUGIN_KEY)
            if (pluginKey != null) {
                fragment = getInstance().getDevice(settingsDeviceId)
                    ?.getPluginIncludingWithoutPermissions(pluginKey)
                    ?.getSettingsFragment(this)
            }
            if (fragment == null) {
                fragment = PluginSettingsListFragment.newInstance(settingsDeviceId)
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
                val stats = DeviceStats.getStatsForDevice(settingsDeviceId)
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

        @JvmStatic
        @JvmOverloads
        fun createIntent(context: Context, deviceId: String, pluginKey: String? = null): Intent =
            Intent(context, PluginSettingsActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
                pluginKey?.let { putExtra(EXTRA_PLUGIN_KEY, it) }
            }
    }
}
