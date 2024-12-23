/*
 * SPDX-FileCopyrightText: 2024 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.base

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.kde.kdeconnect.extensions.getSafeDrawInsets
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat

abstract class BaseActivity : AppCompatActivity() {

    open val isListActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setOnApplyWindowInsetsListenerCompat { decorView, insets ->
                onWindowInsetsChanged(decorView, insets)
                insets
            }
        }
    }

    open fun onWindowInsetsChanged(decorView: View, insets: WindowInsetsCompat) {
        val safeDrawInsets = insets.getSafeDrawInsets()
        decorView.updatePadding(
            top = 0,
            bottom = if (isListActivity) 0 else safeDrawInsets.bottom,
            left = safeDrawInsets.left,
            right = safeDrawInsets.right
        )
    }

}