/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// The values below copy the look of the XML (AppCompat + Material Components) overflow menu so that Compose and XML activities look the same.
// See abc_popup_menu_item_layout.xml and Widget.AppCompat.PopupMenu.Overflow.
private val MenuItemMinWidth = 196.dp
private val MenuItemHorizontalPadding = 16.dp

/**
 * A "three dots" button for the actions of a [KdeTopAppBar].
 * Use [KdeDropdownMenuItem] for the items inside. Call `dismiss` to close the menu.
 */
@Composable
fun KdeOverflowMenu(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            content { expanded = false }
        }
    }
}

@Composable
fun KdeDropdownMenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        modifier = Modifier.widthIn(min = MenuItemMinWidth),
        contentPadding = PaddingValues(horizontal = MenuItemHorizontalPadding),
    )
}
