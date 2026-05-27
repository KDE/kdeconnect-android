/*
 * SPDX-FileCopyrightText: 2024 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.extensions

import android.os.Build
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return
    }
    val originalBottomPadding = paddingBottom
    setOnApplyWindowInsetsListenerCompat { _, insets ->
        val safeInsets = insets.getSafeDrawInsets()
        updatePadding(bottom = originalBottomPadding + safeInsets.bottom)
        insets
    }
}

fun View.setupBottomMargin() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return
    }
    val originalBottomMargin = (layoutParams as MarginLayoutParams).bottomMargin
    setOnApplyWindowInsetsListenerCompat { _, insets ->
        val safeInsets = insets.getSafeDrawInsets()
        updateLayoutParams<MarginLayoutParams> {
            bottomMargin = originalBottomMargin + safeInsets.bottom
        }
        insets
    }
}

fun Modifier.safeDrawPadding(): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        safeDrawingPadding()
    } else {
        Modifier
    }
}

@Composable
fun safeDrawingBottomPadding(): PaddingValues {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
    } else {
        PaddingValues(0.dp)
    }
}