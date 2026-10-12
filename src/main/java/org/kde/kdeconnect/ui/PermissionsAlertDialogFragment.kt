/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.share.ShareSettingsFragment
import org.kde.kdeconnect_tp.R

/**
 * Explains why some permissions are needed and requests them. If setReloadPluginsOnResult was set,
 * reloads the plugins if any is granted.
 */
class PermissionsAlertDialogFragment : AlertDialogFragment() {

    // The dialog stays open while the permission prompt and the document picker are shown, since
    // their results are delivered to this fragment and would be lost if it had been dismissed already
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val reloadPlugins = arguments?.getBoolean(KEY_RELOAD_PLUGINS_ON_RESULT, false) ?: false
        if (reloadPlugins && results.containsValue(true)) {
            KdeConnect.getInstance().reloadAllDevicePlugins()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && results[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true) {
            // To get a writeable path manually on Android 10 and later for Share and Receive Plugin.
            // Otherwise, Receiving files will keep failing until the user chooses a path manually to receive files.
            openDocumentTreeLauncher.launch(null)
            return@registerForActivityResult
        }
        dismiss()
    }

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ShareSettingsFragment.saveStorageLocationPreference(requireContext(), uri)
        }
        dismiss()
    }

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = arguments?.getStringArray(KEY_PERMISSIONS)!!

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                if (!launched) {
                    launched = true
                    requestPermissionsLauncher.launch(permissions)
                }
                return false // Keep the dialog open until the result arrives
            }
        }
    }

    class Builder : AbstractBuilder<Builder, PermissionsAlertDialogFragment>() {
        init {
            setPositiveButton(R.string.ok)
            setNegativeButton(R.string.cancel)
        }

        override fun getThis(): Builder = this

        fun setPermissions(permissions: Array<String>): Builder {
            args.putStringArray(KEY_PERMISSIONS, permissions)
            return getThis()
        }

        fun setReloadPluginsOnResult(reload: Boolean): Builder {
            args.putBoolean(KEY_RELOAD_PLUGINS_ON_RESULT, reload)
            return getThis()
        }

        override fun createFragment(): PermissionsAlertDialogFragment = PermissionsAlertDialogFragment()
    }

    companion object {
        private const val KEY_PERMISSIONS = "Permissions"
        private const val KEY_RELOAD_PLUGINS_ON_RESULT = "ReloadPluginsOnResult"
    }
}
