/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.*
import org.kde.kdeconnect_tp.R
import kotlin.time.Duration.Companion.seconds

class PairingHandler(private val device: Device, private val callback: PairingCallback, var state: PairState) {
    enum class PairState {
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    interface PairingCallback {
        fun incomingPairRequest()

        fun pairingFailed(error: String)

        fun pairingSuccessful()

        fun unpaired()
    }

    private val pairingJob = SupervisorJob()
    private val pairingScope = CoroutineScope(Dispatchers.IO + pairingJob)

    fun packetReceived(np: NetworkPacket) {
        cancelTimer()
        val wantsPair = np.getBoolean("pair")
        if (wantsPair) {
            when (state) {
                PairState.Requested -> pairingDone()
                PairState.RequestedByPeer -> {
                    Log.w(
                        "PairingHandler",
                        "Ignoring second pairing request before the first one timed out"
                    )
                }

                PairState.Paired, PairState.NotPaired -> {
                    if (state == PairState.Paired) {
                        Log.w("PairingHandler", "Received pairing request from a device we already trusted.")
                        // It would be nice to auto-accept the pairing request here, but since the pairing accept and pairing request
                        // messages are identical, this could create an infinite loop if both devices are "accepting" each other pairs.
                        // Instead, unpair and handle as if "NotPaired".
                        state = PairState.NotPaired
                        callback.unpaired()
                    }
                    state = PairState.RequestedByPeer

                    pairingScope.launch {
                        delay(25.seconds)
                        Log.w("PairingHandler", "Unpairing (timeout after we started pairing)")
                        this@PairingHandler.state = PairState.NotPaired
                        callback.pairingFailed(device.context.getString(R.string.error_timed_out))
                    } // Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)

                    callback.incomingPairRequest()
                }
            }
        } else {
            Log.i("PairingHandler", "Unpair request received")
            when (state) {
                PairState.NotPaired -> Log.i("PairingHandler", "Ignoring unpair request for already unpaired device")
                // Requested: We started pairing and got rejected
                // RequestedByPeer: They stared pairing, then cancelled
                PairState.Requested, PairState.RequestedByPeer -> {
                    state = PairState.NotPaired
                    callback.pairingFailed(device.context.getString(R.string.error_canceled_by_other_peer))
                }

                PairState.Paired -> {
                    state = PairState.NotPaired
                    callback.unpaired()
                }
            }
        }
    }

    fun requestPairing() {
        cancelTimer()

        if (state == PairState.Paired) {
            Log.w("PairingHandler", "requestPairing was called on an already paired device")
            callback.pairingFailed(device.context.getString(R.string.error_already_paired))
            return
        }

        if (state == PairState.RequestedByPeer) {
            Log.w("PairingHandler", "Pairing already started by the other end, accepting their request.")
            acceptPairing()
            return
        }

        if (!device.isReachable) {
            callback.pairingFailed(device.context.getString(R.string.error_not_reachable))
            return
        }

        state = PairState.Requested

        pairingScope.launch {
            delay(30.seconds)
            Log.w("PairingHandler", "Unpairing (timeout after receiving pair request)")
            this@PairingHandler.state = PairState.NotPaired
            callback.pairingFailed(device.context.getString(R.string.error_timed_out))
        } // Time to wait for the other to accept

        val statusCallback: Device.SendPacketStatusCallback = object : Device.SendPacketStatusCallback() {
            override fun onSuccess() {}

            override fun onFailure(e: Throwable) {
                cancelTimer()
                Log.e("PairingHandler", "Exception sending pairing request", e)
                this@PairingHandler.state = PairState.NotPaired
                callback.pairingFailed(device.context.getString(R.string.runcommand_notreachable))
            }
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = true
        device.sendPacket(np, statusCallback)
    }

    fun acceptPairing() {
        cancelTimer()
        val stateCallback = object : Device.SendPacketStatusCallback() {
            override fun onSuccess() {
                pairingDone()
            }

            override fun onFailure(e: Throwable) {
                Log.e("PairingHandler", "Exception sending accept pairing packet", e)
                this@PairingHandler.state = PairState.NotPaired
                callback.pairingFailed(device.context.getString(R.string.error_not_reachable))
            }
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = true
        device.sendPacket(np, stateCallback)
    }

    fun cancelPairing() {
        cancelTimer()
        state = PairState.NotPaired
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = false
        device.sendPacket(np)
        callback.pairingFailed(device.context.getString(R.string.error_canceled_by_user))
    }

    @VisibleForTesting
    fun pairingDone() {
        Log.i("PairingHandler", "Pairing done")
        state = PairState.Paired
        kotlin.runCatching {
            callback.pairingSuccessful()
        }.onFailure { e ->
            Log.e("PairingHandler", "Exception in pairingSuccessful callback, unpairing", e)
            state = PairState.NotPaired
        }
    }

    fun unpair() {
        state = PairState.NotPaired
        if (device.isReachable) {
            val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
            np["pair"] = false
            device.sendPacket(np)
        }
        callback.unpaired()
    }

    private fun cancelTimer() {
        pairingJob.cancelChildren()
    }
}
