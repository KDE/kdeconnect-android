/*
 * SPDX-FileCopyrightText: 2024 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.extensions

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

fun View.setOnApplyWindowInsetsListenerCompat(listener: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat) {
    ViewCompat.setOnApplyWindowInsetsListener(this, listener)
}

fun WindowInsetsCompat.getSafeDrawInsets(): Insets {
    return getInsets(
        WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout()
                or WindowInsetsCompat.Type.ime()
    )
}

fun View.setupBottomPadding() {
    val originalBottomPadding = paddingBottom
    setOnApplyWindowInsetsListenerCompat { _, insets ->
        val safeInsets = insets.getSafeDrawInsets()
        updatePadding(bottom = originalBottomPadding + safeInsets.bottom)
        insets
    }
}

fun View.setupBottomMargin() {
    val originalBottomMargin = (layoutParams as MarginLayoutParams).bottomMargin
    setOnApplyWindowInsetsListenerCompat { _, insets ->
        val safeInsets = insets.getSafeDrawInsets()
        updateLayoutParams<MarginLayoutParams> {
            bottomMargin = originalBottomMargin + safeInsets.bottom
        }
        insets
    }
}

@Composable
fun safeDrawingBottomPadding(): PaddingValues {
    return WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
}