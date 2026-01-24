/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun KdeTextButton(
    onClick: () -> Unit,
    modifier: Modifier,
    text: String,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    iconLeft: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        content = {
            iconLeft?.let {
                Icon(imageVector = it, contentDescription = null)
                Spacer(Modifier.width(16.dp))
            }
            Text(text = text)
        }
    )
}

@Composable
fun KdeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    text: String? = null,
    contentDescription: String? = null,
    icon: ImageVector? = null,
) {
    //TODO uncomment when button is widely used
//    val interactionSource = remember { MutableInteractionSource() }
//    val pressedState = interactionSource.collectIsPressedAsState()
//    val cornerSize by animateDpAsState(
//        targetValue = if (pressedState.value) 24.dp else 48.dp,
//        label = "Corner size change on press"
//    )

    Button(
        onClick = onClick,
        modifier = modifier,
//        shape = RoundedCornerShape(cornerSize),
        shape = RoundedCornerShape(24.dp),
        colors = colors,
//        interactionSource = interactionSource,
        content = {
            icon?.let { Icon(imageVector = it, contentDescription = contentDescription ?: text) }
            text?.let { Text(it, maxLines = 1, overflow = Ellipsis) }
        }
    )
}

@Preview
@Composable
fun IconButtonPreview() {
    KdeButton(
        {},
        Modifier.width(120.dp),
        ButtonDefaults.buttonColors(Color.Gray, Color.DarkGray),
        "Button Text",
        null,
        Icons.Default.Build,
    )
}
