/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.material3.Text

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
import org.kde.kdeconnect.extensions.safeDrawPadding
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
        Thread {
            var i = 0
            while (i < chars.length) {
                // Check for exactly 4 spaces starting at position i
                if (
                    i + 3 < chars.length &&
                    chars[i] == ' ' &&
                    chars[i + 1] == ' ' &&
                    chars[i + 2] == ' ' &&
                    chars[i + 3] == ' '
                ) {
                    // Skip these 4 spaces
                    i += 4
                    continue
                }

                // Everything else: send as usual
                val np = NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST)
                when (chars[i]) {
                    '\n' -> np.set("specialKey", 12)
                    '\b' -> np.set("specialKey", 1)
                    else -> np.set("key", chars[i].toString())
                }
                sendKeyPressPacket(np)

                try {
                    Thread.sleep((80..500).random().toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                i++
            }
        }.start()
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
                modifier = Modifier.safeDrawPadding(),
                topBar = { /* unchanged */ },
            ) { scaffoldPadding ->
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .fillMaxSize()
                ) {
                    // Replace KdeTextField with Material3 TextField:
                    TextField(
                        value = userInput.value,
                        onValueChange = { userInput.value = it },
                        label = { Text(stringResource(R.string.click_here_to_type)) },
                        singleLine = false,                // allow newlines
                        maxLines = Int.MAX_VALUE,          // unlimited lines
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Default   // Enter = newline
                        ),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 80.dp)
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    )

                    KdeTextButton(
                        onClick = { sendComposed() },
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .align(Alignment.BottomEnd),
                        enabled = userInput.value.isNotEmpty(),
                        text = stringResource(R.string.send_compose),
                        iconLeft = Icons.Default.Send,
                    )
                }
            }
        }
    }
    }