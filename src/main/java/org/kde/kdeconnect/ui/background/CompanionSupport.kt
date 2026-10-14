/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.kde.kdeconnect.helpers.TrustedDevices

internal object CompanionSupport {
    private const val FILE = "ecosystem_companions"
    var revision by mutableIntStateOf(0)
        private set

    fun supported(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun associations(context: Context): List<AssociationInfo> {
        if (!supported(context)) return emptyList()
        return runCatching { context.getSystemService(CompanionDeviceManager::class.java).myAssociations }
            .getOrDefault(emptyList())
    }

    fun deviceId(context: Context, associationId: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("device_$associationId", null)

    fun associationFor(context: Context, deviceId: String): AssociationInfo? =
        associations(context).firstOrNull { deviceId(context, it.id) == deviceId }

    fun observing(context: Context, associationId: Int): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("observing_$associationId", false)

    fun register(context: Context, info: AssociationInfo, deviceId: String): Boolean {
        if (!TrustedDevices.isTrustedDevice(context, deviceId)) return false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString("device_${info.id}", deviceId) }
        val observed = observe(context, info)
        revision++
        return observed
    }

    fun restore(context: Context) {
        associations(context).forEach { info ->
            val deviceId = deviceId(context, info.id)
            if (deviceId != null) {
                if (TrustedDevices.isTrustedDevice(context, deviceId)) observe(context, info)
                else remove(context, info.id)
            }
        }
    }

    private fun observe(context: Context, info: AssociationInfo): Boolean {
        val observed = runCatching {
            val manager = context.getSystemService(CompanionDeviceManager::class.java)
            if (Build.VERSION.SDK_INT >= 36) {
                manager.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build())
            } else {
                @Suppress("DEPRECATION")
                manager.startObservingDevicePresence(requireNotNull(info.deviceMacAddress).toString())
            }
        }.isSuccess
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("observing_${info.id}", observed) }
        return observed
    }

    fun remove(context: Context, associationId: Int): Boolean {
        if (!supported(context)) return false
        return runCatching {
            context.getSystemService(CompanionDeviceManager::class.java).disassociate(associationId)
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                remove("device_$associationId")
                remove("observing_$associationId")
            }
            revision++
            true
        }.getOrDefault(false)
    }
}
