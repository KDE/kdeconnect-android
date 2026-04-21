/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.ui.compose.KdeTheme

@Composable
private fun KdeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = color,
        style = style,
        fontSize = fontSize,
        textAlign = textAlign,
        maxLines = maxLines,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
fun KdeBodySmallText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit
) = KdeText(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    modifier = modifier,
    textAlign = textAlign,
    color = color,
    fontSize = fontSize,
    maxLines = maxLines,
    onClick = onClick
)

@Composable
fun KdeBodyMediumText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit
) =
    KdeText(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        textAlign = textAlign,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        onClick = onClick
    )

@Composable
fun KdeBodyLargeText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
) =
    KdeText(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
        textAlign = textAlign,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        onClick = onClick
    )

@Composable
fun KdeTitleMediumText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
) =
    KdeText(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
        textAlign = textAlign,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        onClick = onClick
    )

@KdePortraitThemePreviews
@Composable
private fun KdeTextsPreview() {
    KdeTheme(context = LocalContext.current) {
        Column(modifier = Modifier.fillMaxWidth()) {
            KdeBodySmallText(
                text = "KdeBodySmallText",
                color = Color(0xFFCC2222),
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(all = 16.dp),
                onClick = { /* Do nothing */ },
            )
            KdeBodyMediumText(
                text = "KdeBodyMediumText",
                modifier = Modifier.padding(all = 16.dp),
                onClick = { /* Do nothing */ }
            )
            KdeBodyLargeText(
                text = "KdeBodyLargeText",
                modifier = Modifier.padding(all = 16.dp),
                onClick = { /* Do nothing */ },
            )
            KdeTitleMediumText(
                text = "KdeTitleMediumText",
                modifier = Modifier.padding(all = 16.dp),
                onClick = { /* Do nothing */ },
            )
        }
    }
}
