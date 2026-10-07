/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.helpers.readLineBounded
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.text.Charsets.UTF_8

class BluetoothLink(
    context: Context,
    private val connection: ConnectionMultiplexer,
    val input: InputStream,
    val output: OutputStream,
    val remoteAddress: BluetoothDevice,
    val theDeviceInfo: DeviceInfo,
    val linkProvider: BluetoothLinkProvider
) : BaseLink(context, linkProvider) {
    private var continueAccepting = true
    private val receivingThread = Thread(object : Runnable {
        override fun run() {
            try {
                val stream = BufferedInputStream(input)
                while (continueAccepting) {
                    val message = readLineBounded(stream, MAX_PACKET_SIZE)
                    if (!continueAccepting) break
                    processMessage(message)
                }
            } catch (e: IOException) {
                Log.e("BluetoothLink/receiving", "Connection to " + remoteAddress.address + " likely broken.", e)
                disconnect()
            }
        }

        private fun processMessage(message: String) {
            val np = try {
                NetworkPacket.unserialize(message)
            } catch (e: JSONException) {
                Log.e("BluetoothLink/receiving", "Unable to parse message.", e)
                return
            }
            if (np.hasPayloadTransferInfo()) {
                try {
                    val transferUuid = UUID.fromString(np.payloadTransferInfo.getString("uuid"))
                    val payloadInputStream = connection.getChannelInputStream(transferUuid)
                    np.payload = NetworkPacket.Payload(payloadInputStream, np.payloadSize)
                } catch (e: Exception) {
                    Log.e("BluetoothLink/receiving", "Unable to get payload", e)
                }
            }
            packetReceived(np)
        }
    })

    fun startListening() {
        receivingThread.start()
    }

    override fun getName(): String {
        return "BluetoothLink"
    }

    override fun getDeviceInfo(): DeviceInfo {
        return theDeviceInfo
    }

    override fun disconnect() {
        continueAccepting = false
        try {
            connection.close()
        } catch (_: IOException) {
        }
        linkProvider.disconnectedLink(this, remoteAddress)
    }

    @Throws(JSONException::class, IOException::class)
    private fun sendMessage(np: NetworkPacket) {
        val message = np.serialize().toByteArray(UTF_8)
        output.write(message)
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback, sendPayloadFromSameThread: Boolean): Boolean {
        // sendPayloadFromSameThread is ignored, we always send from the same thread!

        return try {
            var transferUuid: UUID? = null
            if (np.hasPayload()) {
                transferUuid = connection.newChannel()
                val payloadTransferInfo = JSONObject()
                payloadTransferInfo.put("uuid", transferUuid.toString())
                np.payloadTransferInfo = payloadTransferInfo
            }
            sendMessage(np)
            if (transferUuid != null) {
                try {
                    connection.getChannelOutputStream(transferUuid).use { payloadStream ->
                        val BUFFER_LENGTH = 1024
                        val buffer = ByteArray(BUFFER_LENGTH)
                        var bytesRead: Int
                        var progress: Long = 0
                        val stream = np.payload!!.inputStream!!
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            progress += bytesRead.toLong()
                            payloadStream.write(buffer, 0, bytesRead)
                            if (np.payloadSize > 0) {
                                callback.onPayloadProgressChanged((100 * progress / np.payloadSize).toInt())
                            }
                        }
                        payloadStream.flush()
                    }
                } catch (e: Exception) {
                    callback.onFailure(e)
                    return false
                }
            }
            callback.onSuccess()
            true
        } catch (e: Exception) {
            callback.onFailure(e)
            false
        }
    }

    companion object {
        private const val MAX_PACKET_SIZE = 32 * 1024 * 1024
    }
}
