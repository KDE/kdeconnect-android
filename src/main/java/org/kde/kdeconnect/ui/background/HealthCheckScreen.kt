/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R

internal data class HealthCheckState(
    val shizuku: ShizukuState,
    val batteryUnrestricted: Boolean,
    val connectedDevice: Boolean,
    val notificationAccess: Boolean,
    val oemName: String,
    val oemInstructions: String,
    val oemAutostartConfirmed: Boolean,
)

@Composable
internal fun HealthCheckScreen(
    state: HealthCheckState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onOpenOemBattery: () -> Unit,
    onConfirmAutostart: () -> Unit,
) {
    FoundationPage(R.string.health_title, onBack) {
        Text(stringResource(R.string.health_summary), style = MaterialTheme.typography.bodyLarge)
        FoundationCard {
            StatusLine(state.shizuku.running && state.shizuku.authorized,
                stringResource(if (state.shizuku.running && state.shizuku.authorized) R.string.health_shizuku_ok else R.string.health_shizuku_missing))
            if (!state.shizuku.running || !state.shizuku.authorized) {
                Text(stringResource(R.string.health_shizuku_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.shizuku_setup_title)) }
            }
        }
        FoundationCard {
            StatusLine(state.batteryUnrestricted,
                stringResource(if (state.batteryUnrestricted) R.string.health_battery_ok else R.string.health_battery_missing))
            if (!state.batteryUnrestricted) {
                Text(stringResource(R.string.health_battery_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenBattery) { Text(stringResource(R.string.health_fix_battery)) }
            }
        }
        FoundationCard {
            StatusLine(state.connectedDevice,
                stringResource(if (state.connectedDevice) R.string.health_connection_ok else R.string.health_connection_missing))
            if (!state.connectedDevice) {
                Text(stringResource(R.string.health_connection_next), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenConnection) { Text(stringResource(R.string.health_open_devices)) }
            }
        }
        FoundationCard {
            StatusLine(state.notificationAccess,
                stringResource(if (state.notificationAccess) R.string.health_notifications_ok else R.string.health_notifications_missing))
            if (!state.notificationAccess) {
                Text(stringResource(R.string.health_notifications_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenNotifications) { Text(stringResource(R.string.health_fix_notifications)) }
            }
        }
        FoundationCard {
            StatusLine(state.oemAutostartConfirmed,
                stringResource(if (state.oemAutostartConfirmed) R.string.health_oem_ok else R.string.health_oem_missing, state.oemName))
            Text(state.oemInstructions, style = MaterialTheme.typography.bodyMedium)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onOpenOemSettings) { Text(stringResource(R.string.health_open_autostart)) }
                TextButton(onClick = onOpenOemBattery) { Text(stringResource(R.string.health_open_oem_battery)) }
                if (!state.oemAutostartConfirmed) {
                    TextButton(onClick = onConfirmAutostart) { Text(stringResource(R.string.health_confirm_autostart)) }
                }
            }
        }
        Button(onClick = onCheck) { Text(stringResource(R.string.background_check_again)) }
    }
}
