/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import com.google.gson.JsonObject
import org.kde.kdeconnect.ui.list.EntryItem

open class CommandEntry(name: String, cmd: String, val key: String) : EntryItem(name, cmd)  {

    constructor(o: JsonObject) : this(o.get("name").asString, o.get("command").asString, o.get("key").asString)

    val name: String
        get() = title

    val command: String
        get() = subtitle!!
}
