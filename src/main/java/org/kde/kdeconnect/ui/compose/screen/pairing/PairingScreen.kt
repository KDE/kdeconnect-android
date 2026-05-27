/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeBodyLargeText
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeBodySmallText
import org.kde.kdeconnect.ui.compose.components.KdeCard
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onDuplicateNamesClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    PairingScreenContent(
        lazyListState = lazyListState,
        pullRefreshState = pullRefreshState,
        uiState = uiState,
        onClick = onClick,
        onWifiSettingsClick = onWifiSettingsClick,
        onNotificationSettingsClick = onNotificationSettingsClick,
        onDuplicateNamesClick = onDuplicateNamesClick,
        onRefresh = onRefresh
    )
}

@Composable
private fun PairingScreenContent(
    lazyListState: LazyListState,
    pullRefreshState: PullToRefreshState,
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onDuplicateNamesClick: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = safeDrawingBottomPadding(),
            state = lazyListState
        ) {
            // Explanations
            item {
                PairingExplanations(
                    uiState = uiState,
                    onWifiSettingsClick = onWifiSettingsClick,
                    onNotificationSettingsClick = onNotificationSettingsClick,
                    onDuplicateNamesClick = onDuplicateNamesClick
                )
            }

            // Connected devices
            item {
                SectionHeader(title = stringResource(id = R.string.category_connected_devices))
            }
            if (uiState.connected.isEmpty()) {
                item {
                    EmptyPlaceholder()
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                itemsIndexed(
                    items = uiState.connected,
                    key = { _, connectedDevice -> connectedDevice.id }) { _, connectedDevice ->
                    CardContent(
                        device = connectedDevice,
                        onClick = onClick
                    )
                }
            }

            // Available devices
            if (uiState.available.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(id = R.string.category_not_paired_devices))
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                itemsIndexed(
                    items = uiState.available,
                    key = { _, availableDevice -> availableDevice.id }) { _, availableDevice ->
                    CardContent(
                        device = availableDevice,
                        onClick = onClick
                    )
                }
            }

            // Remembered devices
            if (uiState.remembered.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(id = R.string.category_remembered_devices))
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                itemsIndexed(
                    items = uiState.remembered,
                    key = { _, rememberedDevice -> rememberedDevice.id }) { _, rememberedDevice ->
                    CardContent(
                        device = rememberedDevice,
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingExplanations(
    uiState: PairingUiState,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onDuplicateNamesClick: () -> Unit
) {
    Column {
        if (uiState.hasDuplicateNames) {
            DuplicateNamesWarning(onClick = onDuplicateNamesClick)
        }

        val someDevicesReachable = uiState.available.isNotEmpty() || uiState.connected.isNotEmpty()

        if (someDevicesReachable || uiState.isWifiAvailable) {
            if (!uiState.hasNotificationsPermission) {
                PairingExplanationRow(
                    text = stringResource(R.string.no_notifications),
                    icon = R.drawable.ic_warning,
                    onClick = onNotificationSettingsClick
                )
            } else if (uiState.isTrustedNetwork) {
                PairingExplanationRow(text = stringResource(R.string.pairing_description))
            } else {
                PairingExplanationRow(
                    text = stringResource(R.string.on_non_trusted_message),
                    icon = R.drawable.ic_warning
                )
            }
        } else {
            PairingExplanationRow(
                text = stringResource(R.string.no_wifi),
                icon = R.drawable.ic_wifi,
                onClick = onWifiSettingsClick
            )
        }
    }
}

@Composable
fun PairingExplanationRow(
    text: String, icon: Int? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        KdeBodyMediumText(
            text = text,
            onClick = { onClick?.invoke() }
        )
    }
}

@Composable
fun DuplicateNamesWarning(
    onClick: () -> Unit
) {
    PairingExplanationRow(
        text = stringResource(id = R.string.pairing_duplicate_names),
        icon = R.drawable.ic_warning,
        onClick = onClick
    )
}

@Composable
fun EmptyPlaceholder() {
    KdeBodySmallText(
        text = stringResource(R.string.device_list_empty),
        modifier = Modifier.padding(10.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CardContent(
    device: DeviceUiModel,
    onClick: (String) -> Unit
) {
    KdeCard(
        modifier = Modifier.fillMaxWidth(),
        content = {
            PairingScreenCardContent(device = device)
        },
        onClick = { onClick(device.id) }
    )
}

@Composable
private fun PairingScreenCardContent(device: DeviceUiModel) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .defaultMinSize(minHeight = 48.dp)
            .padding(all = 32.dp)
    ) {
        if (device.icon > 0) {
            Image(
                imageVector = ImageVector.vectorResource(id = device.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .wrapContentHeight()
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp)
        ) {
            KdeBodyLargeText(
                text = device.name,
                fontSize = 18.sp,
            )
            if (device.summaryRes > 0) {
                KdeBodySmallText(
                    text = stringResource(id = device.summaryRes),
                    color = Color(0xFFCC2222),
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    KdeTheme(context = LocalContext.current) {
        val lazyListState = rememberLazyListState()
        val pullRefreshState = rememberPullToRefreshState()

        PairingScreenContent(
            lazyListState = lazyListState,
            pullRefreshState = pullRefreshState,
            uiState = PairingUiState(
                isWifiAvailable = true,
                hasNotificationsPermission = true,
                isTrustedNetwork = true,
                hasDuplicateNames = true,
                connected = emptyList(),
                available = listOf(
                    DeviceUiModel(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd23_",
                        icon = R.drawable.ic_device_laptop_32dp,
                        name = "Device 1",
                        summaryRes = 0,
                        isReachable = true,
                        isPaired = false
                    ), DeviceUiModel(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd24_",
                        icon = R.drawable.ic_device_desktop_32dp,
                        name = "Device 2",
                        summaryRes = R.string.protocol_version_newer,
                        isReachable = true,
                        isPaired = false
                    )
                ),
                remembered = emptyList(),
                isRefreshing = false
            ),
            onClick = { /* Do nothing */ },
            onWifiSettingsClick = { /* Do nothing */ },
            onNotificationSettingsClick = { /* Do nothing */ },
            onDuplicateNamesClick = { /* Do nothing */ },
            onRefresh = { /* Do nothing */ }
        )
    }
}
