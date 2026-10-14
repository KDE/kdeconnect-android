/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeBodyLargeText
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeBodySmallText
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

private val WideLayoutBreakpoint = 840.dp

@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onLocalNetworkPermissionClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= WideLayoutBreakpoint) {
                    WidePairingContent(
                        lazyListState = lazyListState,
                        uiState = uiState,
                        onClick = onClick,
                        onWifiSettingsClick = onWifiSettingsClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
                    )
                } else {
                    CompactPairingContent(
                        lazyListState = lazyListState,
                        uiState = uiState,
                        onClick = onClick,
                        onWifiSettingsClick = onWifiSettingsClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
                    )
                }
            }
        }
    }
}

@Composable
private fun WidePairingContent(
    lazyListState: LazyListState,
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1440.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DiscoveryHeader()
                RadarCard(modifier = Modifier.fillMaxWidth().weight(1f))
                PairingExplanations(
                    uiState = uiState,
                    onWifiSettingsClick = onWifiSettingsClick,
                    onNotificationSettingsClick = onNotificationSettingsClick,
                    onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1.1f).fillMaxSize(),
                contentPadding = safeDrawingBottomPadding(),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceSections(uiState = uiState, onClick = onClick)
            }
        }
    }
}

@Composable
private fun CompactPairingContent(
    lazyListState: LazyListState,
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = safeDrawingBottomPadding(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DiscoveryHeader() }
        item { RadarCard(modifier = Modifier.fillMaxWidth().height(220.dp)) }
        item {
            PairingExplanations(
                uiState = uiState,
                onWifiSettingsClick = onWifiSettingsClick,
                onNotificationSettingsClick = onNotificationSettingsClick,
                onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
            )
        }
        DeviceSections(uiState = uiState, onClick = onClick)
    }
}

@Composable
private fun DiscoveryHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = stringResource(R.string.add_a_device), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.discovery_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RadarCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "deviceRadar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), repeatMode = RepeatMode.Restart),
        label = "radarSweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), repeatMode = RepeatMode.Reverse),
        label = "radarPulse"
    )
    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2) - 14.dp.toPx()
                for (ring in 1..3) {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.12f + (ring * 0.025f)),
                        radius = radius * ring / 3f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                drawLine(
                    color = lineColor.copy(alpha = 0.42f),
                    start = center,
                    end = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(sweep * 6.283185f) * radius,
                        center.y + kotlin.math.sin(sweep * 6.283185f) * radius
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = lineColor.copy(alpha = pulse),
                    radius = 5.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(center.x + radius * 0.58f, center.y - radius * 0.24f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.searching), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.discovery_devices_appear_here),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun LazyListScope.DeviceSections(uiState: PairingUiState, onClick: (String) -> Unit) {
    item { DeviceSectionHeader(stringResource(R.string.category_connected_devices)) }
    if (uiState.connected.isEmpty()) {
        item { EmptyPlaceholder() }
    } else {
        items(uiState.connected, key = { it.id }) { device -> DeviceCard(device, onClick) }
    }
    if (uiState.available.isNotEmpty()) {
        item { DeviceSectionHeader(stringResource(R.string.category_not_paired_devices)) }
        items(uiState.available, key = { it.id }) { device -> DeviceCard(device, onClick) }
    }
    if (uiState.remembered.isNotEmpty()) {
        item { DeviceSectionHeader(stringResource(R.string.category_remembered_devices)) }
        items(uiState.remembered, key = { it.id }) { device -> DeviceCard(device, onClick) }
    }
}

@Composable
private fun DeviceSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun DeviceCard(device: DeviceUiModel, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        onClick = { onClick(device.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (device.icon > 0) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Image(
                        imageVector = ImageVector.vectorResource(device.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (device.summaryRes > 0) {
                    Text(
                        text = stringResource(device.summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (device.isPaired) {
                Text(
                    text = stringResource(if (device.isReachable) R.string.connected else R.string.offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(text = stringResource(R.string.pair), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PairingExplanations(
    uiState: PairingUiState,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!uiState.hasLocalNetworkPermission) {
            PairingExplanationRow(stringResource(R.string.pairing_missing_local_network_permission), R.drawable.ic_warning, onLocalNetworkPermissionClick)
        }
        if (!uiState.hasNotificationsPermission) {
            PairingExplanationRow(stringResource(R.string.no_notifications), R.drawable.ic_warning, onNotificationSettingsClick)
        }
        if (!uiState.hasLocalNetworkPermission || !uiState.hasNotificationsPermission) return

        if (uiState.hasDuplicateNames) DuplicateNamesWarning()
        val someDevicesReachable = uiState.available.isNotEmpty() || uiState.connected.isNotEmpty()
        if (someDevicesReachable || uiState.isWifiAvailable) {
            if (uiState.isTrustedNetwork) {
                PairingExplanationRow(stringResource(R.string.pairing_description))
            } else {
                PairingExplanationRow(stringResource(R.string.on_non_trusted_message), R.drawable.ic_warning)
            }
        } else {
            PairingExplanationRow(stringResource(R.string.no_wifi), R.drawable.ic_wifi, onWifiSettingsClick)
        }
    }
}

@Composable
fun PairingExplanationRow(text: String, icon: Int? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(10.dp))
        }
        KdeBodyMediumText(text = text, onClick = { onClick?.invoke() })
    }
}

@Composable
fun DuplicateNamesWarning() {
    PairingExplanationRow(stringResource(R.string.pairing_duplicate_names), R.drawable.ic_warning)
}

@Composable
private fun EmptyPlaceholder() {
    KdeBodySmallText(
        text = stringResource(R.string.device_list_empty),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        textAlign = TextAlign.Center
    )
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    KdeTheme(context = LocalContext.current) {
        PairingScreen(
            uiState = PairingUiState(
                isWifiAvailable = true,
                hasNotificationsPermission = true,
                hasLocalNetworkPermission = true,
                isTrustedNetwork = true,
                hasDuplicateNames = false,
                connected = emptyList(),
                available = listOf(
                    DeviceUiModel("pad", R.drawable.ic_device_tablet_32dp, "Pad 8 Pro", 0, true, false),
                    DeviceUiModel("phone", R.drawable.ic_device_phone_32dp, "OnePlus 15", 0, true, false)
                ),
                remembered = emptyList(),
                isRefreshing = false
            ),
            onClick = {}
        )
    }
}
