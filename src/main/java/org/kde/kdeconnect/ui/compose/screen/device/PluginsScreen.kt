/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
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

data class PluginDashboardItem(
    val pluginKey: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val enabled: Boolean,
    val action: Plugin.PluginUiButton?,
)

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginCards: List<PluginDashboardItem>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    onPluginClick: (PluginDashboardItem) -> Unit,
    onPluginEnabledChange: (PluginDashboardItem, Boolean) -> Unit,
    onDeviceMenuClick: () -> Unit,
    actionNeedPermissions: (plugin: Plugin) -> Unit,
    actionNeedOptionalPermissions: (plugin: Plugin) -> Unit,
    localDeviceName: String,
    localDeviceIcon: Int,
    remoteDeviceName: String,
    remoteDeviceIcon: Int,
) {
    PluginsScreenContent(
        pluginsWithButtons = pluginsWithButtons,
        pluginCards = pluginCards,
        pluginsNeedPermissions = pluginsNeedPermissions,
        pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
        onButtonClick = onButtonClick,
        onPluginClick = onPluginClick,
        onPluginEnabledChange = onPluginEnabledChange,
        onDeviceMenuClick = onDeviceMenuClick,
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
    pluginCards: List<PluginDashboardItem>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    onPluginClick: (PluginDashboardItem) -> Unit,
    onPluginEnabledChange: (PluginDashboardItem, Boolean) -> Unit,
    onDeviceMenuClick: () -> Unit,
    actionNeedPermissions: (plugin: Plugin) -> Unit,
    actionNeedOptionalPermissions: (plugin: Plugin) -> Unit,
    localDeviceName: String,
    localDeviceIcon: Int,
    remoteDeviceName: String,
    remoteDeviceIcon: Int,
) {
    val bottomPadding = safeDrawingBottomPadding().calculateBottomPadding()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth < 360.dp -> 1
                maxWidth < 780.dp -> 2
                else -> 3
            }
            val clipboardAction = pluginsWithButtons.firstOrNull {
                it.iconRes == R.drawable.ic_baseline_content_paste_24
            }
            val isWideLayout = maxWidth >= 600.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1280.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(state = rememberScrollState())
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isWideLayout) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.my_ecosystem), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.weight(1f))
                        clipboardAction?.let { button ->
                            Button(
                                onClick = { onButtonClick(button) },
                                modifier = Modifier.heightIn(min = 56.dp),
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
                        IconButton(onClick = onDeviceMenuClick) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.extra_options))
                        }
                    }
                } else {
                    Text(text = stringResource(R.string.my_ecosystem), style = MaterialTheme.typography.headlineMedium)
                }

                ConnectionOverview(
                    localDeviceName = localDeviceName,
                    localDeviceIcon = localDeviceIcon,
                    remoteDeviceName = remoteDeviceName,
                    remoteDeviceIcon = remoteDeviceIcon,
                )

                if (!isWideLayout) clipboardAction?.let { button ->
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
                    plugins = pluginCards,
                    columns = columns,
                    onPluginClick = onPluginClick,
                    onPluginEnabledChange = onPluginEnabledChange,
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
    val (pulse, movingDots) = if (systemAnimationsEnabled()) {
        val transition = rememberInfiniteTransition(label = "connectionPulse")
        val animatedPulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "connectionPulseScale",
        )
        val animatedDots by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1500)),
            label = "connectionDots",
        )
        animatedPulse to animatedDots
    } else {
        0.82f to 0f
    }
    val lineColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier.height(54.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
            repeat(3) { index ->
                val progress = (movingDots + index / 3f) % 1f
                drawCircle(
                    color = lineColor.copy(alpha = 0.86f),
                    radius = 2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width * progress, centerY),
                )
            }
            drawCircle(
                color = lineColor.copy(alpha = 0.16f),
                radius = radius * (1.5f + pulse),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(lineColor, radius = radius * pulse, center = androidx.compose.ui.geometry.Offset(centerX, centerY))
        }
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedVisibility(
                    visible = true,
                    enter = if (systemAnimationsEnabled()) {
                        scaleIn(animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f)) +
                            fadeIn(animationSpec = tween(durationMillis = 180))
                    } else {
                        EnterTransition.None
                    },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun systemAnimationsEnabled(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

@Composable
private fun PluginButtons(
    plugins: List<PluginDashboardItem>,
    columns: Int,
    onPluginClick: (PluginDashboardItem) -> Unit,
    onPluginEnabledChange: (PluginDashboardItem, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        plugins.chunked(columns).forEachIndexed { rowIndex, rowPlugins ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowPlugins.forEachIndexed { columnIndex, plugin ->
                    val buttonIndex = rowIndex * columns + columnIndex
                    AnimatedVisibility(
                        visible = true,
                        modifier = Modifier.weight(1f),
                        enter = if (systemAnimationsEnabled()) {
                            fadeIn(animationSpec = tween(180, delayMillis = buttonIndex * 35)) +
                                slideInVertically(
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 520f),
                                    initialOffsetY = { it / 10 },
                                )
                        } else {
                            EnterTransition.None
                        },
                    ) {
                        PluginButton(
                            plugin = plugin,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 156.dp),
                            onClick = { onPluginClick(plugin) },
                            onEnabledChange = { onPluginEnabledChange(plugin, it) },
                        )
                    }
                }
                repeat(columns - rowPlugins.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginButton(
    plugin: PluginDashboardItem,
    modifier: Modifier,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
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
                        painter = painterResource(id = plugin.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = plugin.enabled, onCheckedChange = onEnabledChange)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
        val previewPlugins = listOf(mprisPlugin, runCommandPlugin, presenterPlugin, pingPlugin)

        PluginsScreenContent(
            pluginsWithButtons = pluginsWithButtons.flatMap { plugin -> plugin.getUiButtons() },
            pluginCards = previewPlugins.map { plugin ->
                val action = plugin.getUiButtons().firstOrNull()
                PluginDashboardItem(
                    pluginKey = plugin.pluginKey,
                    name = plugin.displayName,
                    description = plugin.description,
                    iconRes = action?.iconRes ?: R.drawable.ic_baseline_info_24,
                    enabled = true,
                    action = action,
                )
            },
            pluginsNeedPermissions = pluginsWithoutPermissions,
            pluginsNeedOptionalPermissions = pluginsWithoutOptionalPermissions,
            onButtonClick = { /* Do nothing */ },
            onPluginClick = { /* Do nothing */ },
            onPluginEnabledChange = { _, _ -> /* Do nothing */ },
            onDeviceMenuClick = {},
            actionNeedPermissions = { /* Do nothing */ },
            actionNeedOptionalPermissions = { /* Do nothing */ },
            localDeviceName = "Pad 8 Pro",
            localDeviceIcon = R.drawable.ic_device_tablet_32dp,
            remoteDeviceName = "OnePlus 15",
            remoteDeviceIcon = R.drawable.ic_device_phone_32dp,
        )
    }
}
