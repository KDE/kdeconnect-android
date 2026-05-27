package org.kde.kdeconnect.plugins.runcommand

import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.KdeTopAppBar
import org.kde.kdeconnect_tp.R

@Composable
fun RunCommandScreen(
    plugin: RunCommandPlugin,
    device: Device,
    commandList: SnapshotStateList<CommandEntry>,
    onBackPressedDispatcher: OnBackPressedDispatcher,
    onCopyUrlToClipboard: (CommandEntry) -> Unit,
    onUpdate: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(plugin) {
        val callback = RunCommandPlugin.CommandsChangedCallback {
            scope.launch(Dispatchers.Main.immediate) {
                onUpdate()
            }
        }
        plugin.addCommandsUpdatedCallback(callback)

        onDispose {
            plugin.removeCommandsUpdatedCallback(callback)
        }
    }

    KdeTheme(context) {
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            topBar = { RunCommandAppBar(device.name, onBackPressedDispatcher) },
            floatingActionButton = {
                if (plugin.canAddCommand()) {
                    FloatingActionButton(onClick = {
                        plugin.sendSetupPacket()
                        showDialog = true
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_action_image_edit_24dp),
                            stringResource(R.string.add_command)
                        )
                    }
                }
            },
        ) {
            if (showDialog) {
                AlertDialog(
                    title = {
                        Text(
                            stringResource(R.string.add_command),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Text(stringResource(R.string.add_command_description))
                    },
                    onDismissRequest = {
                        showDialog = false
                        onUpdate()
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog = false
                            onUpdate()
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                    dismissButton = {},
                )
            }

            if (!commandList.isEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize()
                ) {
                    items(commandList) { command ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        var pressOffset by remember { mutableStateOf(IntOffset.Zero) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .pointerInput(command.key) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(
                                                requireUnconsumed = false,
                                                pass = PointerEventPass.Initial,
                                            )
                                            pressOffset = IntOffset(
                                                down.position.x.toInt(),
                                                down.position.y.toInt(),
                                            )
                                        }
                                    }
                                    .combinedClickable(
                                        indication = ripple(color = Color.White),
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { plugin.runCommand(command.key) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuExpanded = true
                                        }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = command.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = command.command,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                    )
                                }
                            }
                            Box(modifier = Modifier.offset { pressOffset }) {
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.copy_url_to_clipboard)) },
                                        onClick = {
                                            menuExpanded = false
                                            onCopyUrlToClipboard(command)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var text = stringResource(R.string.addcommand_explanation)
                    if (!(plugin.canAddCommand())) {
                        text += "\n" + stringResource(R.string.addcommand_explanation2)
                    }
                    Text(text)
                }
            }
        }
    }
}

@Composable
fun RunCommandAppBar(name: String, onBackPressedDispatcher: OnBackPressedDispatcher) {
    KdeTopAppBar(
        title = stringResource(R.string.pref_plugin_runcommand),
        subTitle = name,
        navIconOnClick = { onBackPressedDispatcher.onBackPressed() },
        navIconDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
    )
}