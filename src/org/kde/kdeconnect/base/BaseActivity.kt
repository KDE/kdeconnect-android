/*
 * SPDX-FileCopyrightText: 2024 Mash Kyrielight <fiepi@live.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.base

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import org.kde.kdeconnect.extensions.getImeInsets
import org.kde.kdeconnect.extensions.getSafeDrawInsets
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat

abstract class BaseActivity : AppCompatActivity() {

	open val hasIme: Boolean = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
			window.decorView.setOnApplyWindowInsetsListenerCompat { v, insets ->
				val safeDrawInsets = insets.getSafeDrawInsets()
				val bottom = if (hasIme) safeDrawInsets.bottom.coerceAtLeast(insets.getImeInsets().bottom) else safeDrawInsets.bottom
				v.updatePadding(
					top = 0,
					bottom = bottom,
					left = safeDrawInsets.left,
					right = safeDrawInsets.right
				)
				insets
			}
		}
	}
}