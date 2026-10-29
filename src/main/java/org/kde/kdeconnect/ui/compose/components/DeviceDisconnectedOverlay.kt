/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.appbar.AppBarLayout
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R

/**
 * Covers the content it's placed over with a "device not reachable" message while the device is
 * disconnected, blocking any interaction with what's below. Shows nothing while it's reachable.
 */
@Composable
fun DeviceDisconnectedOverlay(device: Device, modifier: Modifier = Modifier) {
    val isReachable by device.isReachableFlow.collectAsStateWithLifecycle()
    if (isReachable) return

    Column(
        modifier = modifier
            .fillMaxSize()
            // Translucent, so the controls below look grayed out
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            // Consume the touches, so they don't reach the controls below
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phonelink_off_36dp),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.error_not_reachable),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.unreachable_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Adds a [DeviceDisconnectedOverlay] to a View-based screen, below its app bar.
 */
fun CoordinatorLayout.addDeviceDisconnectedOverlay(device: Device) {
    val overlay = ComposeView(context).apply {
        setContent {
            KdeTheme(context) {
                DeviceDisconnectedOverlay(device)
            }
        }
        // Draw on top of elevated views, like buttons
        translationZ = resources.displayMetrics.density * 16
    }
    val layoutParams = CoordinatorLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    ).apply {
        behavior = AppBarLayout.ScrollingViewBehavior()
    }
    addView(overlay, layoutParams)
}
