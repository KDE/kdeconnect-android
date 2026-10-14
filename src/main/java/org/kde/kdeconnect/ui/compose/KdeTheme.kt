/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun KdeTheme(context : Context, content: @Composable () -> Unit) {
    val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (isSystemInDarkTheme()) {
            true -> dynamicDarkColorScheme(context)
            false -> dynamicLightColorScheme(context)
        }
    } else {
        when (isSystemInDarkTheme()) {
            true -> darkColorScheme()
            false -> lightColorScheme()
        }
    }
    // Keep Material You accent roles dynamic while Ecosystem surfaces use the
    // neutral light/dark palette from the mockups.
    val dark = isSystemInDarkTheme()
    val colorScheme = dynamicScheme.copy(
        background = if (dark) Color.Black else Color(0xFFF4F4F6),
        onBackground = if (dark) Color(0xFFF4F4F6) else Color(0xFF1D1D1F),
        surface = if (dark) Color(0xFF1C1C1E) else Color.White,
        onSurface = if (dark) Color(0xFFF4F4F6) else Color(0xFF1D1D1F),
        surfaceVariant = if (dark) Color(0xFF2C2C2E) else Color(0xFFF1F1F3),
        onSurfaceVariant = if (dark) Color(0xFFA1A1A6) else Color(0xFF74747A),
        outline = if (dark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6),
        surfaceTint = Color.Transparent,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
