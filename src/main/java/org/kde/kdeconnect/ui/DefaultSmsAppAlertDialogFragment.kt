/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.core.app.ActivityCompat

class DefaultSmsAppAlertDialogFragment : AlertDialogFragment() {
    private var permissions: Array<String>? = null
    private var requestCode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments ?: return

        permissions = args.getStringArray(KEY_PERMISSIONS)
        requestCode = args.getInt(KEY_REQUEST_CODE, 0)

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                val host = requireActivity()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = host.getSystemService(RoleManager::class.java)
                    if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                        if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                            val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            host.startActivityForResult(roleRequestIntent, requestCode)
                        }
                    }
                } else {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, host.packageName)
                    }
                    host.startActivityForResult(intent, requestCode)
                }

                val perms = permissions
                if (perms != null) {
                    ActivityCompat.requestPermissions(host, perms, requestCode)
                }
                return true
            }
        }
    }

    class Builder : AbstractBuilder<Builder, DefaultSmsAppAlertDialogFragment>() {
        override fun getThis(): Builder = this

        fun setPermissions(permissions: Array<String>): Builder {
            args.putStringArray(KEY_PERMISSIONS, permissions)
            return getThis()
        }

        fun setRequestCode(requestCode: Int): Builder {
            args.putInt(KEY_REQUEST_CODE, requestCode)
            return getThis()
        }

        override fun createFragment(): DefaultSmsAppAlertDialogFragment = DefaultSmsAppAlertDialogFragment()
    }

    companion object {
        private const val KEY_PERMISSIONS = "Permissions"
        private const val KEY_REQUEST_CODE = "RequestCode"
    }
}
