package org.kde.kdeconnect.extensions

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.setOnApplyWindowInsetsListenerCompat(listener: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat) {
	ViewCompat.setOnApplyWindowInsetsListener(this, listener)
}

fun WindowInsetsCompat.getSafeDrawInsets(): Insets {
	val systemBars = getInsets(WindowInsetsCompat.Type.systemBars())
	val cutouts = getInsets(WindowInsetsCompat.Type.displayCutout())
	return Insets.of(
		systemBars.left.coerceAtLeast(cutouts.left),
		systemBars.top.coerceAtLeast(cutouts.top),
		systemBars.right.coerceAtLeast(cutouts.right),
		systemBars.bottom.coerceAtLeast(cutouts.bottom)
	)
}

fun WindowInsetsCompat.getImeInsets(): Insets {
	return getInsets(WindowInsetsCompat.Type.ime())
}
