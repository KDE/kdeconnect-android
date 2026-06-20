/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService.Companion.ForceRefreshConnections
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val _pairingUiState = MutableStateFlow(
        value = PairingUiState(
            isWifiAvailable = false,
            hasNotificationsPermission = false,
            isTrustedNetwork = false,
            hasDuplicateNames = false,
            connected = emptyList(),
            available = emptyList(),
            remembered = emptyList()
        )
    )
    val pairingUiState: StateFlow<PairingUiState> = _pairingUiState.asStateFlow()

    private var allDevices: List<Device> = emptyList()

    fun getDeviceById(deviceId: String): Device? =
        allDevices.find { device -> device.deviceId == deviceId }

    fun updateConnectivity(
        isWifiAvailable: Boolean,
        hasNotificationsPermission: Boolean,
        isTrustedNetwork: Boolean
    ) {
        _pairingUiState.update {
            it.copy(
                isWifiAvailable = isWifiAvailable,
                hasNotificationsPermission = hasNotificationsPermission,
                isTrustedNetwork = isTrustedNetwork
            )
        }
    }

    fun buildUiState(devices: Collection<Device>) =
        viewModelScope.launch(context = Dispatchers.Default) {
            val deviceList = devices.toList()
            this@PairingViewModel.allDevices = deviceList

            val connected = mutableListOf<DeviceUiModel>()
            val available = mutableListOf<DeviceUiModel>()
            val remembered = mutableListOf<DeviceUiModel>()
            val names = mutableSetOf<String>()
            var hasDuplicateNames = false

            for (device in deviceList) {
                if (device.isReachable || device.isPaired) {
                    if (!names.add(device.name)) hasDuplicateNames = true
                    val uiModel = device.toUiModel()
                    when {
                        device.isReachable && device.isPaired -> connected.add(uiModel)
                        device.isReachable && !device.isPaired -> available.add(uiModel)
                        !device.isReachable && device.isPaired -> remembered.add(uiModel)
                    }
                }
            }

            _pairingUiState.update { state ->
                state.copy(
                    hasDuplicateNames = hasDuplicateNames,
                    connected = connected,
                    available = available,
                    remembered = remembered
                )
            }
        }

    fun onRefresh() {
        _pairingUiState.update { uiState -> uiState.copy(isRefreshing = true) }

        ForceRefreshConnections(context = getApplication())

        viewModelScope.launch {
            delay(timeMillis = 1500)
            _pairingUiState.update { uiState -> uiState.copy(isRefreshing = false) }
        }
    }
}
