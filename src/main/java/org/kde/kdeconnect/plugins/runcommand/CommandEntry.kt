/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.runcommand

import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.ui.list.EntryItem

open class CommandEntry : EntryItem {
    val key: String

    @Throws(JSONException::class)
    constructor(o: JSONObject) : this(o.getString("name"), o.getString("command"), o.getString("key"))

    constructor(name: String, cmd: String, key: String) : super(name, cmd) {
        this.key = key
    }

    val name: String
        get() = title

    val command: String
        get() = subtitle!!
}
