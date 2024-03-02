/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.compose

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun KdeTheme(context : Context, content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
