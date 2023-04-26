/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun KdeTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    text: String,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    iconLeft: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        content = {
            iconLeft?.let {
                Icon(imageVector = it, contentDescription = null)
                Spacer(Modifier.width(16.dp))
            }
            Text(text = text)
        }
    )
}