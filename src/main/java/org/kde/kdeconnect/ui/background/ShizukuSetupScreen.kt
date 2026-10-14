/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R

@Composable
internal fun FoundationPage(title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.background_back)) }
                    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                }
                content()
            }
        }
    }
}

@Composable
internal fun StatusLine(ready: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = if (ready) Color(0xFF2DAD69) else MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun FoundationCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ShizukuSetupScreen(state: ShizukuState, onBack: () -> Unit, onCheck: () -> Unit,
    onOpenShizuku: () -> Unit, onOpenDebugging: () -> Unit, onRequestPermission: () -> Unit) {
    FoundationPage(R.string.shizuku_setup_title, onBack) {
        FoundationCard {
            StatusLine(state.running, stringResource(if (state.running) R.string.shizuku_running else R.string.shizuku_stopped))
            StatusLine(state.authorized, stringResource(if (state.authorized) R.string.shizuku_permission_granted else R.string.shizuku_permission_missing))
            Button(onClick = onCheck) { Text(stringResource(R.string.background_check_again)) }
            if (!state.running && state.previouslyRunning) {
                Text(stringResource(R.string.shizuku_restart_hint), color = MaterialTheme.colorScheme.error)
            }
        }
        listOf(Triple(R.string.shizuku_step_install, R.string.shizuku_step_install_hint, R.string.shizuku_open),
            Triple(R.string.shizuku_step_debug, R.string.shizuku_step_debug_hint, R.string.shizuku_open_debug),
            Triple(R.string.shizuku_step_pair, R.string.shizuku_step_pair_hint, R.string.shizuku_open),
            Triple(R.string.shizuku_step_permission, R.string.shizuku_step_permission_hint, R.string.shizuku_allow))
            .forEachIndexed { index, (title, hint, action) ->
                FoundationCard {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(hint), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = when (index) { 1 -> onOpenDebugging; 3 -> onRequestPermission; else -> onOpenShizuku },
                        enabled = index != 3 || (state.running && !state.authorized)) { Text(stringResource(action)) }
                }
            }
        Text(stringResource(R.string.shizuku_reboot_reminder), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
