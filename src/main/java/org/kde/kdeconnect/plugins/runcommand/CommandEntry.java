/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.runcommand;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.ui.list.EntryItem;

class CommandEntry extends EntryItem {
    private final String key;

    public CommandEntry(@NonNull JSONObject o) throws JSONException {
        this(o.getString("name"), o.getString("command"), o.getString("key"));
    }

    public CommandEntry(String name, String cmd, String key) {
        super(name, cmd);
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return getTitle();
    }

    public String getCommand() {
        return getSubtitle();
    }
}
