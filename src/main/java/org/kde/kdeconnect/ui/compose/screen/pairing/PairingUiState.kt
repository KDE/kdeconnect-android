/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

data class PairingUiState(
    val isWifiAvailable: Boolean,
    val hasNotificationsPermission: Boolean,
    val isTrustedNetwork: Boolean,
    val hasDuplicateNames: Boolean,
    val connected: List<DeviceUiModel>,
    val available: List<DeviceUiModel>,
    val remembered: List<DeviceUiModel>
)