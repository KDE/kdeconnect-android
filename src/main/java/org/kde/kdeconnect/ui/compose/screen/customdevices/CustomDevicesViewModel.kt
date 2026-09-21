/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.customdevices

import android.app.Application
import android.content.Context
import android.text.TextUtils
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.ui.CustomDevicesActivity
import java.util.UUID

sealed class PingState {
    data object InProgress : PingState()
    data class Success(val latencyMs: Long) : PingState()
    data object Failed : PingState()
}

// Wraps DeviceHost so Compose can observe ping updates (DeviceHost.ping is mutable, invisible to Compose)
// while id is used to give each instance a fresh identity for LazyColumn's internal mapping
data class DeviceHostUiModel(
    val host: DeviceHost,
    val id: String = UUID.randomUUID().toString(),
    val pingState: PingState = PingState.InProgress
)

sealed class DialogState {
    data object Hidden : DialogState()
    data object AddingNew : DialogState()
    data class Editing(val editId: String, val initialText: String) : DialogState()
}

data class CustomDevicesUiState(
    val devices: List<DeviceHostUiModel> = emptyList(),
    val dialogState: DialogState = DialogState.Hidden
)

sealed class UiEvent {
    data class ShowUndoSnackbar(val deletedHost: String) : UiEvent()
}

sealed class SaveResult {
    data object Success : SaveResult()
    data object InvalidHost : SaveResult()
    data object Duplicate : SaveResult()
}

class CustomDevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(
        application.packageName + "_preferences",
        Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(CustomDevicesUiState())
    val uiState: StateFlow<CustomDevicesUiState> = _uiState.asStateFlow()

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    init {
        val loaded = CustomDevicesActivity.getCustomDeviceList(application)
        _uiState.update {
            it.copy(
                devices = loaded.map { h -> DeviceHostUiModel(h) }
                    .sortedBy { model -> model.host.toString() }
            )
        }
        loaded.forEach { triggerPingFor(it) }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(dialogState = DialogState.AddingNew) }
    }

    fun openEditDialog(id: String) {
        _uiState.update { state ->
            val model = state.devices.find { it.id == id } ?: return@update state
            state.copy(
                dialogState = DialogState.Editing(
                    editId = id,
                    initialText = model.host.toString()
                )
            )
        }
    }

    fun onDialogDismissed() {
        _uiState.update { it.copy(dialogState = DialogState.Hidden) }
    }

    fun onDialogConfirmed(rawInput: String): SaveResult {
        val trimmed = rawInput.trim()
        val host = DeviceHost.toDeviceHostOrNull(trimmed) ?: return SaveResult.InvalidHost

        var result: SaveResult = SaveResult.Success
        var modelsToPersist: List<DeviceHostUiModel>? = null

        _uiState.update { state ->
            when (val dialog = state.dialogState) {
                is DialogState.Editing -> {
                    val isUnchangedEdit = dialog.initialText == host.toString()
                    if (isUnchangedEdit) {
                        result = SaveResult.Success
                        return@update state.copy(dialogState = DialogState.Hidden)
                    }

                    val isDuplicate = state.devices.any {
                        it.id != dialog.editId && it.host.toString() == host.toString()
                    }
                    if (isDuplicate) {
                        result = SaveResult.Duplicate
                        return@update state
                    }

                    val newModels = state.devices.map {
                        if (it.id == dialog.editId) DeviceHostUiModel(host) else it
                    }.sortedBy { model -> model.host.toString() }

                    modelsToPersist = newModels
                    result = SaveResult.Success
                    state.copy(devices = newModels, dialogState = DialogState.Hidden)
                }

                is DialogState.AddingNew -> {
                    val isDuplicate = state.devices.any { it.host.toString() == host.toString() }
                    if (isDuplicate) {
                        result = SaveResult.Duplicate
                        return@update state
                    }

                    val newModels = (state.devices + DeviceHostUiModel(host))
                        .sortedBy { model -> model.host.toString() }

                    modelsToPersist = newModels
                    result = SaveResult.Success
                    state.copy(devices = newModels, dialogState = DialogState.Hidden)
                }

                is DialogState.Hidden -> {
                    result = SaveResult.Success
                    state
                }
            }
        }

        modelsToPersist?.let {
            persistDevicesList(it)
            triggerPingFor(host)
        }

        return result
    }

    fun onDeviceDismissed(id: String) {
        var modelsToPersist: List<DeviceHostUiModel>? = null
        var deletedHost: String? = null

        _uiState.update { state ->
            val newModels = state.devices.mapNotNull {
                if (it.id != id) it
                else {
                    deletedHost = it.host.toString()
                    null
                }
            }

            if (newModels.size != state.devices.size) {
                modelsToPersist = newModels
            }

            state.copy(devices = newModels)
        }

        modelsToPersist?.let {
            persistDevicesList(it)
            if (deletedHost != null) {
                viewModelScope.launch { _uiEvents.send(UiEvent.ShowUndoSnackbar(deletedHost)) }
            }
        }
    }

    fun restoreDevice(deletedHost: String) {
        val device = DeviceHost.toDeviceHostOrNull(deletedHost) ?: return
        var modelsToPersist: List<DeviceHostUiModel>? = null

        _uiState.update { state ->
            if (state.devices.any { it.host.toString() == device.toString() }) return@update state

            val newModels = (state.devices + DeviceHostUiModel(device))
                .sortedBy { model -> model.host.toString() }
            modelsToPersist = newModels

            state.copy(devices = newModels)
        }

        modelsToPersist?.let {
            persistDevicesList(it)
            triggerPingFor(device)
        }
    }

    private fun triggerPingFor(host: DeviceHost) {
        host.checkReachable {
            val resolvedPingState = when (val latency = host.ping?.latency) {
                null -> PingState.Failed
                else -> PingState.Success(latencyMs = latency)
            }

            _uiState.update { state ->
                state.copy(devices = state.devices.map { model ->
                    if (model.host.toString() == host.toString()) model.copy(pingState = resolvedPingState)
                    else model
                })
            }
        }
    }

    private fun persistDevicesList(models: List<DeviceHostUiModel>) {
        prefs.edit {
            putString(
                CustomDevicesActivity.KEY_CUSTOM_DEVICE_LIST_PREFERENCE,
                TextUtils.join(CustomDevicesActivity.IP_DELIM, models.map { it.host })
            )
        }
    }
}