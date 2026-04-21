/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.ui.compose.KdeTheme

@Composable
fun KdeCard(
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit),
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(),
        content = content
    )
}

@PreviewLightDark
@Composable
private fun KdeCardPreview() {
    KdeTheme(context = LocalContext.current) {
        KdeCard(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Text(
                    text = "A very long device name that might wrap into multiple lines",
                    modifier = Modifier.padding(all = 16.dp),
                    style = MaterialTheme.typography.bodyLarge, // textAppearanceMedium
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { /* Do nothing */ }
        )
    }
}
