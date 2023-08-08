/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KdeTopAppBar(
    title: String = stringResource(R.string.kde_connect),
    navIcon: ImageVector = Icons.Default.ArrowBack,
    navIconOnClick: () -> Unit, // = { onBackPressedDispatcher.onBackPressed() }
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