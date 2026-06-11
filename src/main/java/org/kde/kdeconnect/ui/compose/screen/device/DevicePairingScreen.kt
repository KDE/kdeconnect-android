/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.KdeTitleMediumText
import org.kde.kdeconnect_tp.R

@Composable
fun DevicePairingScreen(
    pairStatus: PairingHandler.PairState,
    verificationKey: String,
    pairMessage: String? = null,
    onRequestPairing: () -> Unit,
    onAcceptPairing: () -> Unit,
    onRejectPairing: () -> Unit
) {
    DevicePairingScreenContent(
        pairStatus = pairStatus,
        verificationKey = verificationKey,
        pairMessage = pairMessage,
        onRequestPairing = onRequestPairing,
        onAcceptPairing = onAcceptPairing,
        onRejectPairing = onRejectPairing
    )
}

@Composable
private fun DevicePairingScreenContent(
    pairStatus: PairingHandler.PairState,
    verificationKey: String,
    pairMessage: String? = null,
    onRequestPairing: () -> Unit,
    onAcceptPairing: () -> Unit,
    onRejectPairing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 16.dp)
    ) {
        val finalPairMessage = pairMessage ?: when (pairStatus) {
            PairingHandler.PairState.NotPaired -> stringResource(id = R.string.device_not_paired)
            else -> stringResource(id = R.string.pair_requested)
        }

        KdeTitleMediumText(
            text = finalPairMessage,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        KdeBodyMediumText(
            text = stringResource(id = R.string.pairing_explanation),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (pairStatus != PairingHandler.PairState.NotPaired) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_key),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 5.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KdeTitleMediumText(text = verificationKey)
            }
        }

        if (pairStatus == PairingHandler.PairState.Requested) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        if (pairStatus == PairingHandler.PairState.NotPaired) {
            Button(
                onClick = onRequestPairing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.request_pairing))
            }
        }

        if (pairStatus == PairingHandler.PairState.RequestedByPeer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Button(
                    onClick = onAcceptPairing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.pairing_accept))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRejectPairing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.pairing_reject))
                }
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun DevicePairingScreenNotPairedPreview() {
    KdeTheme(context = LocalContext.current) {
        DevicePairingScreenContent(
            pairStatus = PairingHandler.PairState.NotPaired,
            verificationKey = "",
            onRequestPairing = { /* Do nothing */ },
            onAcceptPairing = { /* Do nothing */ },
            onRejectPairing = { /* Do nothing */ }
        )
    }
}

@KdeThemePreviews
@Composable
private fun DevicePairingScreenRequestedPreview() {
    KdeTheme(context = LocalContext.current) {
        DevicePairingScreenContent(
            pairStatus = PairingHandler.PairState.Requested,
            verificationKey = "123456",
            onRequestPairing = { /* Do nothing */ },
            onAcceptPairing = { /* Do nothing */ },
            onRejectPairing = { /* Do nothing */ }
        )
    }
}

@KdeThemePreviews
@Composable
private fun DevicePairingScreenRequestedByPeerPreview() {
    KdeTheme(context = LocalContext.current) {
        DevicePairingScreenContent(
            pairStatus = PairingHandler.PairState.RequestedByPeer,
            verificationKey = "123456",
            onRequestPairing = { /* Do nothing */ },
            onAcceptPairing = { /* Do nothing */ },
            onRejectPairing = { /* Do nothing */ }
        )
    }
}
