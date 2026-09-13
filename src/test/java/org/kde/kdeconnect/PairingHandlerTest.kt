/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.kde.kdeconnect.helpers.security.SslHelper
import java.util.Base64

class PairingHandlerTest {
    private val certA = SslHelper.parseCertificate(Base64.getMimeDecoder().decode(
        "MIIBkzCCATmgAwIBAgIBATAKBggqhkjOPQQDBDBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRlY2NfOTI2" +
            "MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwHhcNMjMwOTE1MjIw" +
            "MDAwWhcNMzQwOTE1MjIwMDAwWjBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRlY2NfOTI2MV81ZmZlMjcy" +
            "MmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwWTATBgcqhkjOPQIBBggqhkjOPQMB" +
            "BwNCAASqOIKTm5j6x8DKgYSkItLmjCgIXP0gkOW6bmVvloDGsYnvqYLMFGe7YW8g8lT/qPBTEfDOM4UpQ8X6" +
            "jidE+XrnMAoGCCqGSM49BAMEA0gAMEUCIEpk6VNpbt3tfbWDf0TmoJftRq3wAs3Dke7d5vMZlivyAiEA/ZXt" +
            "SRqPjs/2RN9SynKhSUA9/z0PNq6LYoAaC6TdomM="
    ))
    private val certB = SslHelper.parseCertificate(Base64.getMimeDecoder().decode(
        "MIIBkzCCATmgAwIBAgIBATAKBggqhkjOPQQDBDBTMS0wKwYDVQQDDCQxNTdiYmMyOF82ZjJiXzRiMTZfYmQw" +
            "Ml8xMzM0NWMwMjU0M2MxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwHhcNMjQwMTE3MjMw" +
            "MDAwWhcNMzUwMTE3MjMwMDAwWjBTMS0wKwYDVQQDDCQxNTdiYmMyOF82ZjJiXzRiMTZfYmQwMl8xMzM0NWMw" +
            "MjU0M2MxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwWTATBgcqhkjOPQIBBggqhkjOPQMB" +
            "BwNCAAQ5W53rrDJps9v/sszQf0eLtvoGiRbfsY+snO6IJJfi1pFeHDQj2nAE+aTyUYelrcx1eIuqxFHnJTFt" +
            "/HqXwuAvMAoGCCqGSM49BAMEA0gAMEUCIBIk3zKPz/M0c82nvCGFDXGGmfdojHsx3G5DbYNNKqFVAiEAzhBG" +
            "e960/4NDiaVcOplBaeg5xNJKs3Kq+22J6JOii4Y="))

    @Test
    fun getVerificationKey() {
        val timestampA = 1737228658L
        val timestampB = 2737228658L
        Assert.assertEquals("54DC916E", PairingHandler.getVerificationKey(certA, certB, timestampA))
        Assert.assertEquals("54DC916E", PairingHandler.getVerificationKey(certB, certA, timestampA))
        Assert.assertEquals("8C07153A", PairingHandler.getVerificationKey(certA, certB, timestampB))
    }


    @Test
    fun acceptPairingRequiresIncomingRequest() {
        val device = mockk<Device>(relaxed = true)
        val handler = PairingHandler(device, mockk(relaxed = true), PairingHandler.PairState.NotPaired)

        handler.acceptPairing()

        Assert.assertEquals(PairingHandler.PairState.NotPaired, handler.state)
        verify(exactly = 0) { device.sendPacket(any(), any()) }
    }

    @Test
    fun cancelPairingRequiresPairingInProgress() {
        val device = mockk<Device>(relaxed = true)
        val callback = mockk<PairingHandler.PairingCallback>(relaxed = true)
        val handler = PairingHandler(device, callback, PairingHandler.PairState.Paired)

        handler.cancelPairing()

        Assert.assertEquals(PairingHandler.PairState.Paired, handler.state)
        verify(exactly = 0) { device.sendPacket(any()) }
        verify(exactly = 0) { callback.pairingFailed(any()) }
    }

    @Test
    fun getVerificationKeyV7() {
        Assert.assertEquals("F3900DB5", PairingHandler.getVerificationKeyV7(certA, certB))
        Assert.assertEquals("F3900DB5", PairingHandler.getVerificationKeyV7(certB, certA))
        Assert.assertEquals("97A75917", PairingHandler.getVerificationKeyV7(certA, certA))
    }
}
