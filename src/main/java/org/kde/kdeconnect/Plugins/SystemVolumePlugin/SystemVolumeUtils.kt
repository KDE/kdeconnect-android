/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo90690@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SystemVolumePlugin

internal fun getDefaultSink(plugin: SystemVolumePlugin): Sink? {
    return plugin.sinks.firstOrNull { it.isDefault }
}