/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect_tp.R

@Composable
internal fun CompanionSetupScreen(context: Context, busy: Boolean, message: Int?, onBack: () -> Unit,
    onPair: (String) -> Unit, onRemove: (Int) -> Unit, onCheck: () -> Unit, onDiscoverable: () -> Unit) {
    // Presence setup changes can arrive through a system callback while this screen is visible.
    CompanionSupport.revision
    FoundationPage(R.string.companion_title, onBack) {
        Text(stringResource(R.string.companion_explanation), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.companion_steps), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onDiscoverable, enabled = !busy && CompanionSupport.supported(context)) { Text(stringResource(R.string.companion_discoverable)) }
        if (message != null) Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!CompanionSupport.supported(context)) {
            Text(stringResource(R.string.companion_unsupported))
        } else {
            val devices = KdeConnect.getInstance().devices.values.filter {
                it.isPaired && TrustedDevices.isTrustedDevice(context, it.deviceId)
            }
            if (devices.isEmpty()) Text(stringResource(R.string.companion_no_devices))
            devices.forEach { device ->
                FoundationCard {
                    Text(device.name, style = MaterialTheme.typography.titleLarge)
                    val association = CompanionSupport.associationFor(context, device.deviceId)
                    StatusLine(association != null && CompanionSupport.observing(context, association.id),
                        stringResource(if (association == null) R.string.companion_not_paired else R.string.companion_paired))
                    if (association == null) {
                        Button(onClick = { onPair(device.deviceId) }, enabled = !busy) {
                            Text(stringResource(if (busy) R.string.companion_pairing else R.string.companion_pair))
                        }
                    } else {
                        TextButton(onClick = { onRemove(association.id) }, enabled = !busy) { Text(stringResource(R.string.companion_remove)) }
                    }
                }
            }
        }
        Button(onClick = onCheck, enabled = !busy) { Text(stringResource(R.string.background_check_again)) }
    }
}
