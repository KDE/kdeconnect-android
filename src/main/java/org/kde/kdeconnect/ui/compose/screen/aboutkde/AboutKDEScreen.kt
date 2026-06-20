/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.aboutkde

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun AboutKDEScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Header()
        }

        item {
            HtmlCard(stringResource(R.string.about_kde_about))
        }

        item {
            HtmlCard(stringResource(R.string.about_kde_report_bugs_or_wishes))
        }

        item {
            HtmlCard(stringResource(R.string.about_kde_join_kde))
        }

        item {
            HtmlCard(stringResource(R.string.about_kde_support_kde))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.konqi),
                    contentDescription = stringResource(id = R.string.konqi),
                    modifier = Modifier.height(256.dp)
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_kde_48dp),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
        )
        Text(
            text = stringResource(id = R.string.kde_be_free),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun HtmlCard(htmlContent: String) {
    val adjustedHtml = remember(htmlContent) {
        htmlContent
            .replace("</p>", "</p><br>")
            .replace("</h1>", "</h1><br>")
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = AnnotatedString.fromHtml(
                htmlString = adjustedHtml,
                linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@KdeThemePreviews
@Composable
private fun AboutKDEScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        Surface {
            AboutKDEScreen()
        }
    }
}
