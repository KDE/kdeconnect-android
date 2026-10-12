/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.KdeConnect

class StartActivityAlertDialogFragment : AlertDialogFragment() {

    // The dialog stays open while the launched activity is shown, since the result is
    // delivered to this fragment and would be lost if it had been dismissed already
    private val reloadPluginsOnResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        KdeConnect.getInstance().reloadAllDevicePlugins()
        dismiss()
    }

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments

        if (args == null || !args.containsKey(KEY_INTENT_ACTION)) {
            throw RuntimeException("You must call Builder.setIntentAction() to set the intent action")
        }

        val intentAction = args.getString(KEY_INTENT_ACTION)
        val intentUrl = args.getString(KEY_INTENT_URL)
        val reloadPlugins = args.getBoolean(KEY_RELOAD_PLUGINS_ON_RESULT, false)

        callback = object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                val intentUrl = intentUrl
                val intent = if (!intentUrl.isNullOrEmpty()) {
                    Intent(intentAction, intentUrl.toUri())
                } else {
                    Intent(intentAction)
                }

                return if (reloadPlugins) {
                    if (!launched) {
                        launched = true
                        reloadPluginsOnResultLauncher.launch(intent)
                    }
                    false // Keep the dialog open until the result arrives
                } else {
                    requireActivity().startActivity(intent)
                    true
                }
            }
        }
    }

    class Builder : AbstractBuilder<Builder, StartActivityAlertDialogFragment>() {
        override fun getThis(): Builder {
            return this
        }

        fun setIntentAction(intentAction: String): Builder {
            args.putString(KEY_INTENT_ACTION, intentAction)
            return getThis()
        }

        fun setIntentUrl(intentUrl: String): Builder {
            args.putString(KEY_INTENT_URL, intentUrl)
            return getThis()
        }

        fun setReloadPluginsOnResult(reload: Boolean): Builder {
            args.putBoolean(KEY_RELOAD_PLUGINS_ON_RESULT, reload)
            return getThis()
        }

        override fun createFragment(): StartActivityAlertDialogFragment {
            return StartActivityAlertDialogFragment()
        }
    }

    companion object {
        private const val KEY_INTENT_ACTION = "IntentAction"
        private const val KEY_INTENT_URL = "IntentUrl"
        private const val KEY_RELOAD_PLUGINS_ON_RESULT = "ReloadPluginsOnResult"
    }
}
