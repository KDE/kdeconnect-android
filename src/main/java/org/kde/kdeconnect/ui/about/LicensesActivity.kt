/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesEvent
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesScreen
import org.kde.kdeconnect_tp.R

class LicensesActivity : AppCompatActivity() {

    private val scrollEvents = MutableSharedFlow<LicensesEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            KdeTheme(this) {
                Scaffold(
                    topBar = {
                        LicensesTopBar(
                            onNavigateBack = { onBackPressedDispatcher.onBackPressed() },
                            onScrollToTop = {
                                lifecycleScope.launch {
                                    scrollEvents.emit(
                                        LicensesEvent.ScrollToTop
                                    )
                                }
                            },
                            onScrollToBottom = {
                                lifecycleScope.launch {
                                    scrollEvents.emit(
                                        LicensesEvent.ScrollToBottom
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    LicensesScreen(
                        eventFlow = scrollEvents,
                        contentPadding = padding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesTopBar(
    onNavigateBack: () -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.licenses)) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back_black_24dp),
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onScrollToTop) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_upward_black_24dp),
                    contentDescription = "Scroll to top"
                )
            }
            IconButton(onClick = onScrollToBottom) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_downward_black_24dp),
                    contentDescription = "Scroll to bottom"
                )
            }
        }
    )
}