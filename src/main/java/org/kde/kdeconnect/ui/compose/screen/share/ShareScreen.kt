/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    devices: List<DeviceUiModel>,
    intentHasUrl: Boolean,
    isRefreshing: Boolean,
    onDeviceClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val state = rememberLazyListState()
    val sectionTitle = if (intentHasUrl) {
        "${stringResource(R.string.unreachable_device_url_share_text)} ${stringResource(R.string.share_to)}"
    } else {
        stringResource(R.string.share_to)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val requestedColumns = when {
                    maxWidth < 600.dp -> 1
                    maxWidth < 1080.dp -> 2
                    else -> 3
                }
                val columns = minOf(requestedColumns, devices.size.coerceAtLeast(1))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1280.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = sectionTitle, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = stringResource(R.string.share_to_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ShareDeviceList(
                        state = state,
                        devices = devices,
                        columns = columns,
                        onDeviceClick = onDeviceClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareDeviceList(
    state: LazyListState,
    devices: List<DeviceUiModel>,
    columns: Int,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = safeDrawingBottomPadding(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (devices.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = stringResource(R.string.device_list_empty),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(devices.chunked(columns), key = { row -> row.joinToString { it.id } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { index, device ->
                        AnimatedVisibility(
                            visible = true,
                            modifier = Modifier.weight(1f),
                            enter = fadeIn(tween(240, delayMillis = index * 45)) +
                                slideInVertically(tween(240, delayMillis = index * 45)) { it / 12 }
                        ) {
                            ShareDeviceCard(
                                device = device,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onDeviceClick(device.id) }
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDeviceCard(
    device: DeviceUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.heightIn(min = 116.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (device.icon > 0) {
                    Image(
                        imageVector = ImageVector.vectorResource(device.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(if (device.isReachable) R.string.connected else R.string.offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.share_action_send),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@KdeThemePreviews
@Composable
private fun ShareScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        ShareScreen(
            devices = listOf(
                DeviceUiModel("laptop", R.drawable.ic_device_laptop_32dp, "Ecosystem Laptop", 0, true, true),
                DeviceUiModel("phone", R.drawable.ic_device_phone_32dp, "OnePlus 15", 0, true, true)
            ),
            intentHasUrl = false,
            isRefreshing = false,
            onDeviceClick = {},
            onRefresh = {}
        )
    }
}
