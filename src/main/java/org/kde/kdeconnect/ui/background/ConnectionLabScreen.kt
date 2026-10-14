/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ConnectionLabScreen(
    rows: List<ConnectionLabRow>,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onDrop: () -> Unit,
    onFinish: () -> Unit,
    onExport: () -> Unit,
) {
    var scenario by remember { mutableStateOf("Normal Wi-Fi") }
    FoundationPage(R.string.connection_lab_title, onBack) {
        Text(stringResource(R.string.connection_lab_description), style = MaterialTheme.typography.bodyLarge)
        FoundationCard {
            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                label = { Text(stringResource(R.string.connection_lab_scenario)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { onStart(scenario) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_lab_start))
            }
            Button(onClick = onDrop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_lab_mark_drop))
            }
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_lab_finish))
            }
            TextButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_lab_export))
            }
        }
        if (rows.isEmpty()) {
            FoundationCard { Text(stringResource(R.string.connection_lab_empty)) }
        } else {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            rows.forEach { row ->
                FoundationCard {
                    Text(row.scenario, style = MaterialTheme.typography.titleMedium)
                    Text(dateFormat.format(Date(row.timestamp)), style = MaterialTheme.typography.bodySmall)
                    Text("${row.noticeMs} ms / ${row.recoveryMs} ms · ${row.chosenLink}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${row.lostItems} lost · ${row.duplicateItems} repeated · ${row.clipboardDelayMs} ms clipboard · ${"%.3f".format(row.transferMbps)} Mbps",
                        style = MaterialTheme.typography.bodyMedium)
                    if (row.batteryDrainPercent >= 0) Text("${row.batteryDrainPercent}% battery drain")
                    if (row.reason.isNotBlank()) Text(row.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
