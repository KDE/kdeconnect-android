/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.os.Bundle
import androidx.core.app.ActivityCompat
import org.kde.kdeconnect_tp.R

class PermissionsAlertDialogFragment : AlertDialogFragment() {
    private var permissions: Array<String>? = null
    private var requestCode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args == null || !args.containsKey(KEY_PERMISSIONS)) {
            throw RuntimeException("You must call Builder.setPermission() to set the array of needed permissions")
        }

        permissions = args.getStringArray(KEY_PERMISSIONS)
        requestCode = args.getInt(KEY_REQUEST_CODE, 0)

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                val perms = permissions
                if (perms != null) {
                    ActivityCompat.requestPermissions(requireActivity(), perms, requestCode)
                }
                return true
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

        fun setRequestCode(requestCode: Int): Builder {
            args.putInt(KEY_REQUEST_CODE, requestCode)
            return getThis()
        }

        override fun createFragment(): PermissionsAlertDialogFragment = PermissionsAlertDialogFragment()
    }

    companion object {
        private const val KEY_PERMISSIONS = "Permissions"
        private const val KEY_REQUEST_CODE = "RequestCode"
    }
}
