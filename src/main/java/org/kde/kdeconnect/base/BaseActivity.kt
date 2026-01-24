/*
 * SPDX-FileCopyrightText: 2024 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.viewbinding.ViewBinding
import org.kde.kdeconnect.extensions.getSafeDrawInsets
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat

abstract class BaseActivity<VB: ViewBinding> : AppCompatActivity() {

    protected abstract val binding: VB

    open val isScrollable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            binding.root.setOnApplyWindowInsetsListenerCompat { view, insets ->
                onWindowInsetsChanged(view, insets)
                insets
            }
        }
    }

    open fun onWindowInsetsChanged(view: View, insets: WindowInsetsCompat) {
        val safeDrawInsets = insets.getSafeDrawInsets()
        view.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (isScrollable) 0 else safeDrawInsets.bottom
            leftMargin = safeDrawInsets.left
            rightMargin = safeDrawInsets.right
        }
    }

}