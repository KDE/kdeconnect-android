/*
 SPDX-FileCopyrightText: 2021 Art Pinch <leonardo906@mail.ru>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

interface VolumeKeyListener {
    fun onVolumeUp()
    fun onVolumeDown()
}
