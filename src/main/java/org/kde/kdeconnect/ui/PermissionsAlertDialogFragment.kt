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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = arguments?.getStringArray(KEY_PERMISSIONS)!!

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                ActivityCompat.requestPermissions(requireActivity(), permissions, 0)
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

        override fun createFragment(): PermissionsAlertDialogFragment = PermissionsAlertDialogFragment()
    }

    companion object {
        private const val KEY_PERMISSIONS = "Permissions"
    }
}
