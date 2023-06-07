/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.kde.kdeconnect_tp.R

@Composable
fun KdeTextField(modifier: Modifier = Modifier, input: MutableState<String>, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally
            ) {
        var value by rememberSaveable { input
        }
        OutlinedTextField(
            modifier = modifier,
            value = value,
            onValueChange ={
                value =it
            },
            label ={
                Text(text = label)
            }
        )
    }

}

@SuppressLint("UnrememberedMutableState")
@Preview(showSystemUi = true, showBackground = true)
@Composable
fun Preview() {
    KdeTextField(
        input = mutableStateOf(""),
        label = stringResource(R.string.click_here_to_type),
    )
}