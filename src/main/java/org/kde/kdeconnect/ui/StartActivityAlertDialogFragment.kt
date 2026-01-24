/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

class StartActivityAlertDialogFragment : AlertDialogFragment() {
    private var intentAction: String? = null
    private var intentUrl: String? = null
    private var requestCode = 0
    private var startForResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments

        if (args == null || !args.containsKey(KEY_INTENT_ACTION)) {
            throw RuntimeException("You must call Builder.setIntentAction() to set the intent action")
        }

        intentAction = args.getString(KEY_INTENT_ACTION)
        intentUrl = args.getString(KEY_INTENT_URL)
        requestCode = args.getInt(KEY_REQUEST_CODE, 0)
        startForResult = args.getBoolean(KEY_START_FOR_RESULT)

        check(!startForResult || args.containsKey(KEY_REQUEST_CODE)) {
            "You requested startForResult but you did not set the requestCode"
        }

        setCallback(object : Callback() {
            override fun onPositiveButtonClicked(): Boolean {
                val intentUrl = intentUrl
                val intent = if (!intentUrl.isNullOrEmpty()) {
                    Intent(intentAction, intentUrl.toUri())
                } else {
                    Intent(intentAction)
                }

                if (startForResult) {
                    requireActivity().startActivityForResult(intent, requestCode)
                } else {
                    requireActivity().startActivity(intent)
                }
                return true
            }
        })
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

        fun setRequestCode(requestCode: Int): Builder {
            args.putInt(KEY_REQUEST_CODE, requestCode)

            return getThis()
        }

        fun setStartForResult(startForResult: Boolean): Builder {
            args.putBoolean(KEY_START_FOR_RESULT, startForResult)

            return getThis()
        }

        override fun createFragment(): StartActivityAlertDialogFragment {
            return StartActivityAlertDialogFragment()
        }
    }

    companion object {
        private const val KEY_INTENT_ACTION = "IntentAction"
        private const val KEY_INTENT_URL = "IntentUrl"
        private const val KEY_REQUEST_CODE = "RequestCode"
        private const val KEY_START_FOR_RESULT = "StartForResult"
    }
}
