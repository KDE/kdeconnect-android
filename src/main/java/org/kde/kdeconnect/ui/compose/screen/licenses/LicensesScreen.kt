/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.licenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R
import androidx.compose.ui.platform.LocalResources

@Composable
fun LicensesScreen(
    eventFlow: SharedFlow<LicensesEvent>,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val resources = LocalResources.current
    var licenseChunks by remember { mutableStateOf<List<String>>(emptyList()) }

    val listState = rememberLazyListState()

    LaunchedEffect(resources) {
        withContext(Dispatchers.IO) {
            val chunks = resources
                .openRawResource(R.raw.license)
                .bufferedReader()
                .use { it.readText() }
                .split("-".repeat(80))
                .filter { it.isNotBlank() }
            licenseChunks = chunks
        }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is LicensesEvent.ScrollToTop -> listState.animateScrollToItem(0)
                is LicensesEvent.ScrollToBottom -> {
                    if (licenseChunks.isNotEmpty()) {
                        listState.animateScrollToItem(licenseChunks.size - 1)
                    }
                }
            }
        }
    }

    if (licenseChunks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }


    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        itemsIndexed(
            items = licenseChunks,
            key = { index, _ -> index }
        ) { index, chunk ->
            Text(
                text = chunk.trim(),
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (index < licenseChunks.size - 1) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun LicensesScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        LicensesScreen(eventFlow = remember { MutableSharedFlow() })
    }
}