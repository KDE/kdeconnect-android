/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.customdevices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.KdeTopAppBar
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDevicesRoute(
    onNavigateUp: () -> Unit,
    viewModel: CustomDevicesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val deletedLabel = stringResource(R.string.custom_device_deleted)

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedLabel,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreDevice(event.deletedHost)
                    }
                }
            }
        }
    }

    CustomDevicesScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onAddClicked = viewModel::openAddDialog,
        onDeviceClicked = viewModel::openEditDialog,
        onDeviceDismissed = viewModel::onDeviceDismissed,
        onDialogConfirm = viewModel::onDialogConfirmed,
        onDialogDismiss = viewModel::onDialogDismissed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDevicesScreen(
    uiState: CustomDevicesUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onAddClicked: () -> Unit,
    onDeviceClicked: (String) -> Unit,
    onDeviceDismissed: (String) -> Unit,
    onDialogConfirm: (String) -> SaveResult,
    onDialogDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            KdeTopAppBar(
                title = stringResource(R.string.custom_devices_settings),
                navIconDescription = stringResource(
                    androidx.appcompat.R.string.abc_action_bar_up_description
                ),
                navIconOnClick = onNavigateUp
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClicked) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_device_dialog_title)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = uiState.devices.isEmpty(),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .padding(innerPadding)
                    .align(Alignment.Center)
            ) {
                Text(
                    text = stringResource(R.string.custom_device_list_help),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding
            ) {
                items(
                    items = uiState.devices,
                    key = { model -> model.id }
                ) { model ->
                    CustomDeviceItem(
                        model = model,
                        onClick = { onDeviceClicked(model.id) },
                        onDismissed = { onDeviceDismissed(model.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    val dialogState = uiState.dialogState
    if (dialogState !is DialogState.Hidden) {
        AddEditDeviceDialog(
            dialogState = dialogState,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDeviceItem(
    model: DeviceHostUiModel,
    onClick: () -> Unit,
    onDismissed: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        onDismiss = { onDismissed() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = model.host.toString(),
                style = MaterialTheme.typography.bodyLarge
            )

            val statusText = when (val ping = model.pingState) {
                is PingState.InProgress -> stringResource(R.string.ping_in_progress)
                is PingState.Success -> stringResource(R.string.ping_result, ping.latencyMs)
                is PingState.Failed -> stringResource(R.string.ping_failed)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddEditDeviceDialog(
    dialogState: DialogState,
    onConfirm: (String) -> SaveResult,
    onDismiss: () -> Unit
) {
    val initialText = when (dialogState) {
        DialogState.AddingNew -> ""
        is DialogState.Editing -> dialogState.initialText
        DialogState.Hidden -> ""
    }

    var text by rememberSaveable { mutableStateOf(initialText) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val invalidLabel = stringResource(R.string.device_host_invalid)
    val duplicateLabel = stringResource(R.string.device_host_duplicate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_device_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    errorMessage = null
                },
                label = { Text(stringResource(R.string.add_device_hint)) },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { msg ->
                    { Text(msg, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (onConfirm(text)) {
                        SaveResult.Success -> Unit // Do Nothing
                        SaveResult.InvalidHost -> errorMessage = invalidLabel
                        SaveResult.Duplicate -> errorMessage = duplicateLabel
                    }
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@KdeThemePreviews
@Composable
private fun CustomDevicesScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        CustomDevicesScreen(
            uiState = CustomDevicesUiState(
                devices = listOf(
                    DeviceHostUiModel(
                        host = DeviceHost.toDeviceHostOrNull("192.168.1.1")!!,
                        pingState = PingState.Success(latencyMs = 12)
                    ),
                    DeviceHostUiModel(
                        host = DeviceHost.toDeviceHostOrNull("kde-laptop.local")!!,
                        pingState = PingState.InProgress
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onAddClicked = {},
            onDeviceClicked = {},
            onDeviceDismissed = {},
            onDialogConfirm = { SaveResult.Success },
            onDialogDismiss = {}
        )
    }
}

@KdeThemePreviews
@Composable
private fun CustomDevicesScreenEmptyPreview() {
    KdeTheme(context = LocalContext.current) {
        CustomDevicesScreen(
            uiState = CustomDevicesUiState(devices = emptyList()),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onAddClicked = {},
            onDeviceClicked = {},
            onDeviceDismissed = {},
            onDialogConfirm = { SaveResult.Success },
            onDialogDismiss = {}
        )
    }
}
