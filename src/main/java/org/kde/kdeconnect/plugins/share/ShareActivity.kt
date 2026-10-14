/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.os.Bundle
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding

class ShareActivity : BaseActivity<ActivityShareBinding>() {

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
    }

    override val binding: ActivityShareBinding by lazy { ActivityShareBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MainActivity.isShareIntent(intent)) {
            finish()
            return
        }

        val shareIntent = Intent(this, MainActivity::class.java).apply {
            action = intent.action
            intent.type?.let { type -> setDataAndType(intent.data, type) }
            clipData = intent.clipData
            putExtras(intent)
            addFlags(
                intent.flags and (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    ),
            )
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(shareIntent)
        finish()
    }
}
