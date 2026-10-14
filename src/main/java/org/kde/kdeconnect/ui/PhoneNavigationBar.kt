/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R

@Composable
internal fun PhoneNavigationBar(selectedEntry: Int, onSelect: (Int) -> Unit) {
    val selectedTab = if (selectedEntry == 2 || selectedEntry == 4) selectedEntry else 1
    Surface(color = MaterialTheme.colorScheme.background) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(40.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1 to R.string.devices, 4 to R.string.share, 2 to R.string.settings).forEach { (entry, title) ->
                    val selected = selectedTab == entry
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(entry) }),
                        shape = RoundedCornerShape(28.dp),
                        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
