/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.share

import android.app.Activity
import android.content.ClipData
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.view.DragAndDropPermissions
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.plugins.share.ShareTransferRecord
import org.kde.kdeconnect.plugins.share.TransferStatus
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
internal fun ShareScreen(
    targetDeviceName: String?,
    targetDeviceConnected: Boolean,
    transfers: List<ShareTransferRecord>,
    isDropActive: Boolean,
    onChooseFiles: () -> Unit,
    onFilesDropped: (List<Uri>, DragAndDropPermissions?) -> Unit,
    onDropActiveChanged: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wideLayout = maxWidth >= 700.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1440.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = if (wideLayout) 28.dp else 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.share_files_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = targetDeviceName?.let { name ->
                                if (targetDeviceConnected) stringResource(R.string.share_sent_to, name)
                                else stringResource(R.string.share_device_offline, name)
                            } ?: stringResource(R.string.share_device_not_selected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (wideLayout) {
                        ChooseFilesButton(
                            enabled = targetDeviceConnected,
                            onClick = onChooseFiles,
                        )
                    }
                }

                if (wideLayout) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        DropArea(
                            targetDeviceName = targetDeviceName,
                            targetDeviceConnected = targetDeviceConnected,
                            isDropActive = isDropActive,
                            showChooseFilesPrompt = false,
                            onChooseFiles = onChooseFiles,
                            onFilesDropped = onFilesDropped,
                            onDropActiveChanged = onDropActiveChanged,
                            modifier = Modifier.weight(1.05f).heightIn(min = 320.dp),
                        )
                        RecentTransfers(
                            transfers = transfers,
                            modifier = Modifier.weight(1f),
                            scrollable = true,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item {
                            DropArea(
                                targetDeviceName = targetDeviceName,
                                targetDeviceConnected = targetDeviceConnected,
                                isDropActive = isDropActive,
                                showChooseFilesPrompt = true,
                                onChooseFiles = onChooseFiles,
                                onFilesDropped = onFilesDropped,
                                onDropActiveChanged = onDropActiveChanged,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 205.dp),
                            )
                        }
                        item { RecentHeader() }
                        if (transfers.isEmpty()) {
                            item { EmptyRecentCard() }
                        } else {
                            items(transfers, key = ShareTransferRecord::id) { transfer ->
                                TransferCard(transfer = transfer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseFilesButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Text(
            text = stringResource(R.string.share_choose_files),
            modifier = Modifier.padding(horizontal = 14.dp),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun DropArea(
    targetDeviceName: String?,
    targetDeviceConnected: Boolean,
    isDropActive: Boolean,
    showChooseFilesPrompt: Boolean,
    onChooseFiles: () -> Unit,
    onFilesDropped: (List<Uri>, DragAndDropPermissions?) -> Unit,
    onDropActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val borderColor = if (isDropActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val context = LocalContext.current
    val dropTarget = remember(targetDeviceConnected, onFilesDropped, onDropActiveChanged) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = onDropActiveChanged(true)
            override fun onExited(event: DragAndDropEvent) = onDropActiveChanged(false)
            override fun onEnded(event: DragAndDropEvent) = onDropActiveChanged(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val androidEvent = event.toAndroidDragEvent()
                val clipData = androidEvent.clipData ?: return false
                val uris = clipData.toUris()
                if (uris.isEmpty()) return false
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    (context as? Activity)?.requestDragAndDropPermissions(androidEvent)
                } else {
                    null
                }
                onDropActiveChanged(false)
                onFilesDropped(uris, permissions)
                return true
            }
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = targetDeviceConnected, onClick = onChooseFiles)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    targetDeviceConnected && event.toAndroidDragEvent().clipData?.toUris()?.isNotEmpty() == true
                },
                target = dropTarget,
            )
            .drawWithContent {
                drawContent()
                val dash = 9.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.72f)),
                    ),
                )
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FileGlyph(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.background, upload = true)
                }
            }
            Text(
                text = if (targetDeviceConnected && !showChooseFilesPrompt) stringResource(R.string.share_drop_files_here)
                else if (targetDeviceConnected) stringResource(R.string.share_choose_files)
                else stringResource(R.string.share_choose_device_first),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = when {
                    targetDeviceName == null -> stringResource(R.string.share_device_not_selected)
                    !targetDeviceConnected -> stringResource(R.string.share_device_offline_short)
                    else -> stringResource(R.string.share_sent_to, targetDeviceName)
                },
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentHeader() {
    Text(
        text = stringResource(R.string.share_recent).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun RecentTransfers(transfers: List<ShareTransferRecord>, modifier: Modifier = Modifier, scrollable: Boolean) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RecentHeader()
        if (transfers.isEmpty()) {
            EmptyRecentCard()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.weight(1f) else Modifier)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp),
            ) {
                transfers.forEachIndexed { index, transfer ->
                    TransferRow(transfer)
                    if (index != transfers.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecentCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = stringResource(R.string.share_recent_empty),
            modifier = Modifier.padding(22.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransferCard(transfer: ShareTransferRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        TransferRow(transfer = transfer, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
private fun TransferRow(transfer: ShareTransferRecord, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = when (transfer.status) {
        TransferStatus.SENDING -> stringResource(R.string.share_status_sending)
        TransferStatus.SENT -> stringResource(R.string.share_status_sent)
        TransferStatus.RECEIVING -> stringResource(R.string.share_status_receiving)
        TransferStatus.RECEIVED -> stringResource(R.string.share_status_received)
        TransferStatus.FAILED -> stringResource(R.string.share_status_failed)
    }
    val size = if (transfer.size < 0) {
        stringResource(R.string.share_file_size_unknown)
    } else {
        Formatter.formatShortFileSize(context, transfer.size)
    }
    val showProgress = transfer.status == TransferStatus.SENDING || transfer.status == TransferStatus.RECEIVING
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FileGlyph(modifier = Modifier.size(21.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = transfer.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(size)
                        append(" · ")
                        if (showProgress) {
                            append(transfer.progress)
                            append("% · ")
                        }
                        append(status)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (transfer.status == TransferStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!showProgress) {
                Text(
                    text = when (transfer.status) {
                        TransferStatus.SENT, TransferStatus.RECEIVED -> stringResource(R.string.share_status_done)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showProgress) {
            LinearProgressIndicator(
                progress = { transfer.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(start = 60.dp),
                color = MaterialTheme.colorScheme.onBackground,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun FileGlyph(modifier: Modifier = Modifier, color: Color, upload: Boolean = false) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stroke = width * 0.075f
        val path = Path().apply {
            moveTo(width * 0.22f, height * 0.12f)
            lineTo(width * 0.58f, height * 0.12f)
            lineTo(width * 0.8f, height * 0.34f)
            lineTo(width * 0.8f, height * 0.88f)
            lineTo(width * 0.22f, height * 0.88f)
            close()
            moveTo(width * 0.58f, height * 0.12f)
            lineTo(width * 0.58f, height * 0.34f)
            lineTo(width * 0.8f, height * 0.34f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke))
        if (upload) {
            drawLine(color, androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.64f), androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.92f), strokeWidth = stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(width * 0.37f, height * 0.76f), androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.63f), strokeWidth = stroke)
            drawLine(color, androidx.compose.ui.geometry.Offset(width * 0.63f, height * 0.76f), androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.63f), strokeWidth = stroke)
        }
    }
}

@KdeThemePreviews
@Composable
private fun ShareScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        ShareScreen(
            targetDeviceName = "OnePlus 15",
            targetDeviceConnected = true,
            transfers = emptyList(),
            isDropActive = false,
            onChooseFiles = {},
            onFilesDropped = { _, permissions -> permissions?.release() },
            onDropActiveChanged = {},
        )
    }
}

private fun ClipData.toUris(): List<Uri> = (0 until itemCount).mapNotNull { index ->
    getItemAt(index).uri
}
