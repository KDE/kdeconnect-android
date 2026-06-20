/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.extensions.device

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceType.LAPTOP
import org.kde.kdeconnect.DeviceType.PHONE
import org.kde.kdeconnect.DeviceType.TABLET
import org.kde.kdeconnect.DeviceType.TV
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

fun Device.toUiModel() = DeviceUiModel(
    id = deviceId,
    icon = iconDrawable,
    name = name,
    summaryRes = if (compareProtocolVersion() > 0) R.string.protocol_version_newer else 0,
    isReachable = isReachable,
    isPaired = isPaired
)