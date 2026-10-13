/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object JsonHelper {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true // when unserializing, do not throw if the JSON has more fields than the @Serializable class
        encodeDefaults = true // when serializing, write fields that contain their default values
        maxNestingDepth = 64 // when unserializing, don't try to decode infinitely deep packets
    }
}
