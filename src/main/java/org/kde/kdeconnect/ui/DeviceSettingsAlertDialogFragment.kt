/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.os.Bundle

class DeviceSettingsAlertDialogFragment : AlertDialogFragment() {
    private var pluginKey: String? = null
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args == null || !args.containsKey(KEY_PLUGIN_KEY)) {
            throw RuntimeException("You must call Builder.setPluginKey() to set the plugin")
        }
        if (!args.containsKey(KEY_DEVICE_ID)) {
            throw RuntimeException("You must call Builder.setDeviceId() to set the device")
        }

        pluginKey = args.getString(KEY_PLUGIN_KEY)
        deviceId = args.getString(KEY_DEVICE_ID)

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                val currentDeviceId = deviceId ?: return true
                val currentPluginKey = pluginKey ?: return true
                requireActivity().startActivity(
                    PluginSettingsActivity.createIntent(requireActivity(), currentDeviceId, currentPluginKey)
                )
                return true
            }
        }
    }

    class Builder : AbstractBuilder<Builder, DeviceSettingsAlertDialogFragment>() {
        override fun getThis(): Builder = this

        fun setPluginKey(pluginKey: String): Builder {
            args.putString(KEY_PLUGIN_KEY, pluginKey)
            return getThis()
        }

        fun setDeviceId(deviceId: String): Builder {
            args.putString(KEY_DEVICE_ID, deviceId)
            return getThis()
        }

        override fun createFragment(): DeviceSettingsAlertDialogFragment = DeviceSettingsAlertDialogFragment()
    }

    companion object {
        private const val KEY_PLUGIN_KEY = "PluginKey"
        private const val KEY_DEVICE_ID = "DeviceId"
    }
}
