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
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
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
            val sb = StringBuilder()
            try {
                val reader: Reader = InputStreamReader(input, UTF_8)
                val buf = CharArray(512)
                while (continueAccepting) {
                    while (sb.indexOf("\n") == -1 && continueAccepting) {
                        var charsRead: Int
                        if (reader.read(buf).also { charsRead = it } > 0) {
                            sb.append(buf, 0, charsRead)
                        }
                        if (charsRead < 0) {
                            disconnect()
                            return
                        }
                    }
                    if (!continueAccepting) break
                    val endIndex = sb.indexOf("\n")
                    if (endIndex != -1) {
                        val message = sb.substring(0, endIndex + 1)
                        sb.delete(0, endIndex + 1)
                        processMessage(message)
                    }
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
}
