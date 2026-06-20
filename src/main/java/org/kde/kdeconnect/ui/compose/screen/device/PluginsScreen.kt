/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
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
    action: (plugin: Plugin) -> Unit
) {
    PluginsScreenContent(
        pluginsWithButtons = pluginsWithButtons,
        pluginsNeedPermissions = pluginsNeedPermissions,
        pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
        onButtonClick = onButtonClick,
        action = action
    )
}

@Composable
private fun PluginsScreenContent(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    action: (plugin: Plugin) -> Unit
) {
    Surface {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            val numColumns = LocalResources.current.getInteger(R.integer.plugins_columns)

            PluginButtons(
                buttons = pluginsWithButtons,
                numColumns = numColumns,
                onButtonClick = onButtonClick
            )
            Spacer(modifier = Modifier.padding(vertical = 6.dp))
            if (pluginsNeedPermissions.isNotEmpty()) {
                PluginsWithoutPermissions(
                    title = stringResource(id = R.string.plugins_need_permission),
                    plugins = pluginsNeedPermissions,
                    action = action
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
            }
            if (pluginsNeedOptionalPermissions.isNotEmpty()) {
                PluginsWithoutPermissions(
                    title = stringResource(id = R.string.plugins_need_optional_permission),
                    plugins = pluginsNeedOptionalPermissions,
                    action = action
                )
            }
        }
    }
}

@Composable
private fun PluginButtons(
    buttons: List<Plugin.PluginUiButton>,
    numColumns: Int,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        val buttonIter = buttons.iterator()

        while (buttonIter.hasNext()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(times = numColumns) {
                    if (buttonIter.hasNext()) {
                        val button = buttonIter.next()

                        PluginButton(
                            button = button,
                            modifier = Modifier.weight(1f),
                            onClick = { onButtonClick(button) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.semantics { role = Role.Button },
        onClick = onClick
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(id = button.iconRes),
                modifier = Modifier.padding(top = 12.dp),
                contentDescription = null
            )
            Text(
                text = button.name,
                maxLines = 2,
                minLines = 2,
                fontSize = 18.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PluginsWithoutPermissions(
    title: String,
    plugins: Collection<Plugin>,
    action: (plugin: Plugin) -> Unit
) {
    Text(
        text = title,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() }
    )
    plugins.forEach { plugin ->
        Text(
            text = plugin.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { action(plugin) }
                .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                .semantics { role = Role.Button }
        )
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        val pluginsWithButtons = listOf(
            MprisPlugin(),
            RunCommandPlugin(),
            PresenterPlugin()
        )

        pluginsWithButtons.forEach { plugin ->
            plugin.setContext(
                context = LocalContext.current,
                device = null
            )
        }
        PluginsScreenContent(
            pluginsWithButtons = pluginsWithButtons.flatMap { plugin -> plugin.getUiButtons() },
            pluginsNeedPermissions = emptyList(),
            pluginsNeedOptionalPermissions = emptyList(),
            onButtonClick = { /* Do nothing */ },
            action = { /* Do nothing */ }
        )
    }
}
