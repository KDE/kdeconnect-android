/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.UserInterface.compose.KdeTextButton
import org.kde.kdeconnect.UserInterface.compose.KdeTextField
import org.kde.kdeconnect.UserInterface.compose.KdeTheme
import org.kde.kdeconnect.UserInterface.compose.KdeTopAppBar
import org.kde.kdeconnect_tp.R

private const val INPUT_CACHE_KEY = "compose_send_input_cache"

class ComposeSendActivity : AppCompatActivity() {
    private var deviceId: String? = null
    private val userInput = mutableStateOf(String())
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefs.getString(INPUT_CACHE_KEY, null)?.let { userInput.value = it }

        setContent { ComposeSendScreen() }

        deviceId = intent.getStringExtra("org.kde.kdeconnect.Plugins.MousePadPlugin.deviceId")
    }

    override fun onStop() {
        super.onStop()
        with(prefs.edit()) {
            if (userInput.value.isNotBlank()) putString(INPUT_CACHE_KEY, userInput.value) else remove(INPUT_CACHE_KEY)
            apply()
        }
    }

    private fun sendChars(chars: String) {
        val np = NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST)
        np["key"] = chars
        sendKeyPressPacket(np)
    }

    private fun sendKeyPressPacket(np: NetworkPacket) {
        try {
            Log.d("packed", np.serialize())
        } catch (e: Exception) {
            Log.e("KDE/ComposeSend", "Exception", e)
        }
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin::class.java)
        if (plugin == null) {
            finish()
            return
        }
        plugin.sendKeyboardPacket(np)
    }

    private fun sendComposed() {
        sendChars(userInput.value)
        clearComposeInput()
    }

    private fun clearComposeInput() {
        userInput.value = String()
    }

    @Composable
    private fun ComposeSendScreen() {
        KdeTheme(this) {
            Scaffold(
                topBar = {
                    KdeTopAppBar(
                        title = stringResource(R.string.compose_send_title),
                        navIconOnClick = { onBackPressedDispatcher.onBackPressed() },
                        navIconDescription = getString(androidx.appcompat.R.string.abc_action_bar_up_description),
                        actions = {
                            KdeTextButton(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = { clearComposeInput() },
                                text = stringResource(R.string.clear_compose),
                            )
                        }
                    )
                },
            ) { scaffoldPadding ->
                Box(modifier = Modifier.padding(scaffoldPadding).fillMaxSize()) {
                    KdeTextField(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 80.dp)
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        input = userInput,
                        label = stringResource(R.string.click_here_to_type),
                    )
                    KdeTextButton(
                        onClick = { sendComposed() },
                        modifier = Modifier.padding(all = 16.dp).align(Alignment.BottomEnd),
                        enabled = userInput.value.isNotEmpty(),
                        text = stringResource(R.string.send_compose),
                        iconLeft = Icons.Default.Send,
                    )
                }
            }
        }
    }
}
