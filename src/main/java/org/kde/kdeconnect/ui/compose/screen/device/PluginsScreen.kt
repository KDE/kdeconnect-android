/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.ping.PingPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    actionNeedPermissions: (plugin: Plugin) -> Unit,
    actionNeedOptionalPermissions: (plugin: Plugin) -> Unit,
    localDeviceName: String,
    localDeviceIcon: Int,
    remoteDeviceName: String,
    remoteDeviceIcon: Int,
) {
    PluginsScreenContent(
        pluginsWithButtons = pluginsWithButtons,
        pluginsNeedPermissions = pluginsNeedPermissions,
        pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
        onButtonClick = onButtonClick,
        actionNeedPermissions = actionNeedPermissions,
        actionNeedOptionalPermissions = actionNeedOptionalPermissions,
        localDeviceName = localDeviceName,
        localDeviceIcon = localDeviceIcon,
        remoteDeviceName = remoteDeviceName,
        remoteDeviceIcon = remoteDeviceIcon,
    )
}

@Composable
private fun PluginsScreenContent(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    actionNeedPermissions: (plugin: Plugin) -> Unit,
    actionNeedOptionalPermissions: (plugin: Plugin) -> Unit,
    localDeviceName: String,
    localDeviceIcon: Int,
    remoteDeviceName: String,
    remoteDeviceIcon: Int,
) {
    val bottomPadding = safeDrawingBottomPadding().calculateBottomPadding()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth < 360.dp -> 1
                maxWidth < 780.dp -> 2
                else -> 3
            }
            val clipboardAction = pluginsWithButtons.firstOrNull {
                it.iconRes == R.drawable.ic_baseline_content_paste_24
            }
            val dashboardButtons = if (clipboardAction == null) {
                pluginsWithButtons
            } else {
                pluginsWithButtons - clipboardAction
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1280.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(state = rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConnectionOverview(
                    localDeviceName = localDeviceName,
                    localDeviceIcon = localDeviceIcon,
                    remoteDeviceName = remoteDeviceName,
                    remoteDeviceIcon = remoteDeviceIcon,
                )

                clipboardAction?.let { button ->
                    Button(
                        onClick = { onButtonClick(button) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(painterResource(button.iconRes), contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(text = button.name, style = MaterialTheme.typography.titleMedium)
                    }
                }

                PluginButtons(
                    buttons = dashboardButtons,
                    columns = columns,
                    onButtonClick = onButtonClick,
                )

                if (pluginsNeedPermissions.isNotEmpty()) {
                    PluginPermissionSection(
                        title = stringResource(id = R.string.plugins_need_permission),
                        plugins = pluginsNeedPermissions,
                        action = actionNeedPermissions,
                    )
                }
                if (pluginsNeedOptionalPermissions.isNotEmpty()) {
                    PluginPermissionSection(
                        title = stringResource(id = R.string.plugins_need_optional_permission),
                        plugins = pluginsNeedOptionalPermissions,
                        action = actionNeedOptionalPermissions,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionOverview(
    localDeviceName: String,
    localDeviceIcon: Int,
    remoteDeviceName: String,
    remoteDeviceIcon: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.my_ecosystem),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceEndpoint(name = localDeviceName, icon = localDeviceIcon)
                ConnectionLine(modifier = Modifier.weight(1f))
                DeviceEndpoint(name = remoteDeviceName, icon = remoteDeviceIcon)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = CircleShape,
                        color = Color(0xFF2EAD65),
                    ) {}
                    Text(text = stringResource(R.string.connected), style = MaterialTheme.typography.labelLarge)
                    Text(text = "|", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.wifi), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DeviceEndpoint(name: String, icon: Int) {
    Column(
        modifier = Modifier.widthIn(min = 76.dp, max = 132.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = name,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(14.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "connectionPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connectionPulseScale",
    )
    val lineColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.height(54.dp).padding(horizontal = 4.dp)) {
        val centerY = size.height / 2
        val centerX = size.width / 2
        val radius = 7.dp.toPx()
        drawLine(
            color = lineColor.copy(alpha = 0.8f),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())),
        )
        drawCircle(lineColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(0f, centerY))
        drawCircle(lineColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, centerY))
        drawCircle(
            color = lineColor.copy(alpha = 0.16f),
            radius = radius * (1.5f + pulse),
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(lineColor, radius = radius * pulse, center = androidx.compose.ui.geometry.Offset(centerX, centerY))
    }
}

@Composable
private fun PluginButtons(
    buttons: List<Plugin.PluginUiButton>,
    columns: Int,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buttons.chunked(columns).forEachIndexed { rowIndex, rowButtons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowButtons.forEachIndexed { columnIndex, button ->
                    val buttonIndex = rowIndex * columns + columnIndex
                    AnimatedVisibility(
                        visible = true,
                        modifier = Modifier.weight(1f),
                        enter = fadeIn(animationSpec = tween(260, delayMillis = buttonIndex * 35)) +
                            slideInVertically(
                                animationSpec = tween(260, delayMillis = buttonIndex * 35),
                                initialOffsetY = { it / 10 },
                            ),
                    ) {
                        PluginButton(
                            button = button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp),
                            onClick = { onButtonClick(button) },
                        )
                    }
                }
                repeat(columns - rowButtons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginButton(
    button: Plugin.PluginUiButton,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .animateContentSize()
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        painter = painterResource(id = button.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.open_plugin),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = button.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.plugin_action_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PluginPermissionSection(
    title: String,
    plugins: Collection<Plugin>,
    action: (plugin: Plugin) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        plugins.forEach { plugin ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = { action(plugin) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = plugin.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                    text = stringResource(R.string.allow_permission),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        val mprisPlugin = MprisPlugin().also { it.setContext(LocalContext.current, null) }
        val runCommandPlugin = RunCommandPlugin().also { it.setContext(LocalContext.current, null) }
        val presenterPlugin = PresenterPlugin().also { it.setContext(LocalContext.current, null) }
        val pingPlugin = PingPlugin().also { it.setContext(LocalContext.current, null) }
        val clipboardPlugin = ClipboardPlugin().also { it.setContext(LocalContext.current, null) }

        val pluginsWithButtons = listOf(mprisPlugin, runCommandPlugin, presenterPlugin, pingPlugin)
        val pluginsWithoutPermissions = listOf(clipboardPlugin, presenterPlugin)
        val pluginsWithoutOptionalPermissions = listOf(mprisPlugin, presenterPlugin)

        PluginsScreenContent(
            pluginsWithButtons = pluginsWithButtons.flatMap { plugin -> plugin.getUiButtons() },
            pluginsNeedPermissions = pluginsWithoutPermissions,
            pluginsNeedOptionalPermissions = pluginsWithoutOptionalPermissions,
            onButtonClick = { /* Do nothing */ },
            actionNeedPermissions = { /* Do nothing */ },
            actionNeedOptionalPermissions = { /* Do nothing */ },
            localDeviceName = "Pad 8 Pro",
            localDeviceIcon = R.drawable.ic_device_tablet_32dp,
            remoteDeviceName = "OnePlus 15",
            remoteDeviceIcon = R.drawable.ic_device_phone_32dp,
        )
    }
}
