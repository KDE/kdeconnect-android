/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncSettings
import org.kde.kdeconnect_tp.R

@Composable
internal fun ClipboardSyncScreen(
    settings: ClipboardSyncSettings,
    watcherActive: Boolean,
    automaticAvailable: Boolean,
    onBack: () -> Unit,
    onSettingsChanged: (ClipboardSyncSettings) -> Unit,
    onCheck: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    FoundationPage(R.string.clipboard_sync_title, onBack) {
        Text(stringResource(R.string.clipboard_sync_summary), style = MaterialTheme.typography.bodyLarge)
        FoundationCard {
            ClipboardSwitch(
                title = stringResource(R.string.clipboard_sync_enabled),
                summary = stringResource(R.string.clipboard_sync_enabled_summary),
                checked = settings.enabled,
                onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) },
            )
            if (settings.enabled) {
                StatusLine(automaticAvailable, stringResource(
                    when {
                        automaticAvailable -> R.string.clipboard_sync_watcher_active
                        settings.trustedWifiOnly && watcherActive -> R.string.clipboard_sync_wifi_fallback
                        else -> R.string.clipboard_sync_manual_fallback
                    },
                ))
                if (!watcherActive) {
                    Text(stringResource(R.string.clipboard_sync_shizuku_hint), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Button(onClick = onOpenShizuku) {
                        Text(stringResource(R.string.shizuku_setup_title))
                    }
                } else if (settings.trustedWifiOnly && !automaticAvailable) {
                    Text(stringResource(R.string.clipboard_sync_trusted_wifi_hint), style = MaterialTheme.typography.bodyMedium)
                }
                androidx.compose.material3.TextButton(onClick = onCheck) {
                    Text(stringResource(R.string.background_check_again))
                }
            }
        }
        FoundationCard {
            ClipboardSwitch(
                title = stringResource(R.string.clipboard_sync_trusted_wifi),
                summary = stringResource(R.string.clipboard_sync_trusted_wifi_summary),
                checked = settings.trustedWifiOnly,
                onCheckedChange = { onSettingsChanged(settings.copy(trustedWifiOnly = it)) },
            )
            if (settings.trustedWifiOnly) {
                Text(stringResource(R.string.clipboard_sync_trusted_wifi_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FoundationCard {
            ClipboardSwitch(
                title = stringResource(R.string.clipboard_sync_plain_text),
                summary = stringResource(R.string.clipboard_sync_plain_text_summary),
                checked = settings.plainTextOnly,
                onCheckedChange = { onSettingsChanged(settings.copy(plainTextOnly = it)) },
            )
        }
        FoundationCard {
            ClipboardSwitch(
                title = stringResource(R.string.clipboard_sync_confirmation),
                summary = stringResource(R.string.clipboard_sync_confirmation_summary),
                checked = settings.showConfirmation,
                onCheckedChange = { onSettingsChanged(settings.copy(showConfirmation = it)) },
            )
        }
    }
}

@Composable
private fun ClipboardSwitch(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
