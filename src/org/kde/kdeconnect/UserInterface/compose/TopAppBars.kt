/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdeTopAppBar(
    title: String,
    navIcon: ImageVector,
    navIconOnClick: () -> Unit,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = navIconOnClick, content = { Icon(navIcon, null) })
        },
        title = { Text(title) },
        actions = actions
    )
}