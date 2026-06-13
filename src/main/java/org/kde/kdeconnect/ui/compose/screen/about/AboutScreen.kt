/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.about.AboutData
import org.kde.kdeconnect.ui.about.AboutPerson
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun AboutScreen(
    aboutData: AboutData,
    onEasterEggTriggered: () -> Unit,
    onReportBugClicked: () -> Unit,
    onDonateClicked: () -> Unit,
    onSourceCodeClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    onAboutKdeClicked: () -> Unit,
    onWebsiteClicked: () -> Unit
) {
    val bottomPadding = safeDrawingBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = bottomPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        item {
            AppInfoCard(
                aboutData = aboutData,
                onEasterEggTriggered = onEasterEggTriggered
            )
        }

        item {
            ActionButtons(
                aboutData = aboutData,
                onReportBugClicked = onReportBugClicked,
                onDonateClicked = onDonateClicked,
                onSourceCodeClicked = onSourceCodeClicked,
                onLicensesClicked = onLicensesClicked,
                onAboutKdeClicked = onAboutKdeClicked,
                onWebsiteClicked = onWebsiteClicked
            )
        }

        item {
            AuthorsCard(aboutData = aboutData)
        }
    }
}

@Composable
private fun AppInfoCard(
    aboutData: AboutData,
    onEasterEggTriggered: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var firstTapMillis by remember { mutableStateOf<Long?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val currentMillis = System.currentTimeMillis()
            if (firstTapMillis == null) {
                firstTapMillis = currentMillis
            }

            tapCount++

            if (tapCount == 3) {
                if (currentMillis - firstTapMillis!! <= 500) {
                    onEasterEggTriggered()
                }
                tapCount = 0
                firstTapMillis = null
            } else if (currentMillis - firstTapMillis!! > 500) {
                tapCount = 1
                firstTapMillis = currentMillis
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = aboutData.icon),
                contentDescription = null,
                modifier = Modifier.size(52.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text(
                    text = aboutData.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(id = R.string.version, aboutData.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtons(
    aboutData: AboutData,
    onReportBugClicked: () -> Unit,
    onDonateClicked: () -> Unit,
    onSourceCodeClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    onAboutKdeClicked: () -> Unit,
    onWebsiteClicked: () -> Unit
) {
    val buttons = remember(aboutData) {
        val list = mutableListOf<@Composable () -> Unit>()
        if (aboutData.bugURL != null) {
            list.add {
                ActionIconTextButton(
                    textRes = R.string.report_bug,
                    iconRes = R.drawable.ic_baseline_bug_report_24,
                    onClick = onReportBugClicked
                )
            }
        }
        if (aboutData.donateURL != null) {
            list.add {
                ActionIconTextButton(
                    textRes = R.string.donate,
                    iconRes = R.drawable.ic_baseline_attach_money_24,
                    onClick = onDonateClicked
                )
            }
        }
        if (aboutData.sourceCodeURL != null) {
            list.add {
                ActionIconTextButton(
                    textRes = R.string.source_code,
                    iconRes = R.drawable.ic_baseline_code_24,
                    onClick = onSourceCodeClicked
                )
            }
        }

        list.add {
            ActionIconTextButton(
                textRes = R.string.licenses,
                iconRes = R.drawable.ic_baseline_gavel_24,
                onClick = onLicensesClicked
            )
        }
        list.add {
            ActionIconTextButton(
                textRes = R.string.about_kde,
                iconRes = R.drawable.ic_kde_24dp,
                onClick = onAboutKdeClicked
            )
        }

        if (aboutData.websiteURL != null) {
            list.add {
                ActionIconTextButton(
                    textRes = R.string.website,
                    iconRes = R.drawable.ic_baseline_web_24,
                    onClick = onWebsiteClicked
                )
            }
        }
        list
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val buttonWidth = 84.dp
        val totalNeededWidth = buttonWidth.times(buttons.size)

        val maxItemsInRow = if (totalNeededWidth <= this.maxWidth) buttons.size else 3

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            maxItemsInEachRow = maxItemsInRow
        ) {
            buttons.forEach { button -> button() }
        }
    }
}

@Composable
private fun ActionIconTextButton(
    @StringRes textRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .size(84.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    color = MaterialTheme.colorScheme.onBackground,
                    bounded = false
                ),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = textRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuthorsCard(aboutData: AboutData) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.authors),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            aboutData.authors.forEach { author ->
                AuthorItemRow(author = author)
            }

            aboutData.authorsFooterText?.let { footerResId ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = footerResId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthorItemRow(author: AboutPerson) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = author.name,
            style = MaterialTheme.typography.bodyLarge
        )
        if (author.task != null) {
            Text(
                text = stringResource(id = author.task),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@KdeThemePreviews
@Composable
private fun AboutScreenPreview() {
    val sampleAboutData = AboutData(
        name = "KDE Connect",
        icon = R.drawable.icon,
        versionName = "1.27.0",
        bugURL = "https://bugs.kde.org",
        websiteURL = "https://kdeconnect.kde.org",
        sourceCodeURL = "https://invent.kde.org/network/kdeconnect-android",
        donateURL = "https://www.kde.org/community/donations",
        authorsFooterText = R.string.everyone_else
    ).apply {
        authors += AboutPerson("Albert Vaca Cintora", R.string.maintainer_and_developer)
        authors += AboutPerson("Aleix Pol", R.string.developer)
    }

    KdeTheme(context = LocalContext.current) {
        Surface {
            AboutScreen(
                aboutData = sampleAboutData,
                onEasterEggTriggered = {},
                onReportBugClicked = {},
                onDonateClicked = {},
                onSourceCodeClicked = {},
                onLicensesClicked = {},
                onAboutKdeClicked = {},
                onWebsiteClicked = {}
            )
        }
    }
}
