/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import kotlinx.serialization.Serializable
import org.kde.kdeconnect.ui.list.EntryItem

@Serializable
data class CommandInfo(val name: String, val command: String, val key: String)

open class CommandEntry(info: CommandInfo) : EntryItem(info.name, info.command)  {

    val key: String = info.key

    val name: String
        get() = title

    val command: String
        get() = subtitle!!
}
