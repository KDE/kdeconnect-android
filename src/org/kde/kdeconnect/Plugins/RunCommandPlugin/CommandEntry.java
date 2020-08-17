/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import org.kde.kdeconnect.UserInterface.List.EntryItem;

class CommandEntry extends EntryItem {
    private final String key;

    public CommandEntry(String name, String cmd, String key) {
        super(name, cmd);
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return title;
    }

    public String getCommand() {
        return subtitle;
    }
}
