/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends.lan

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.lan.LanLinkProvider.Companion.openServerSocketOnFreePort
import org.kde.kdeconnect.extensions.closeSafe
import org.kde.kdeconnect.helpers.LineTooLongException
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.helpers.readLineBounded
import org.kde.kdeconnect.helpers.security.SslHelper.convertToSslSocket
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.Volatile

class LanLink(
    context: Context,
    linkProvider: BaseLinkProvider,
    override var deviceInfo: DeviceInfo,
    @Volatile private var socket: SSLSocket
) : BaseLink(context, linkProvider) {

    override val name = "LanLink"

    override fun disconnect() {
        Log.i(LOG_TAG, "Disconnect socket: ${socket.hashCode()}")
        try {
            socket.close()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Disconnect error", e)
        }
    }

    fun reset(newSocket: SSLSocket, newDeviceInfo: DeviceInfo) {
        deviceInfo = newDeviceInfo
        val oldSocket = socket
        socket = newSocket
        oldSocket.closeSafe() // This should cancel the readThread
        startListening()
    }

    fun startListening() {
        val currentSocket = socket
        // Create a thread to take care of incoming data for the socket
        ThreadHelper.execute {
            try {
                val stream = BufferedInputStream(currentSocket.inputStream)
                while (true) {
                    val packet: String = try {
                        readLineBounded(stream, MAX_PACKET_SIZE)
                    } catch (_: LineTooLongException) {
                        continue
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (packet.isEmpty()) {
                        continue
                    }
                    val np = NetworkPacket.unserialize(packet)
                    receivedNetworkPacket(np)
                }
            } catch (e: Exception) {
                Log.i(LOG_TAG, "Socket closed: ${currentSocket.hashCode()}. Reason: ${e.message}")
                currentSocket.closeSafe()

                // Wait a bit because we might receive a new socket meanwhile
                try { Thread.sleep(300) } catch (_: InterruptedException) { }

                val thereIsaANewSocket = (currentSocket !== socket)
                if (!thereIsaANewSocket) {
                    Log.i(LOG_TAG, "Socket closed and there's no new socket, disconnecting device")
                    linkProvider.onConnectionLost(this@LanLink)
                }
            }
        }
    }

    @WorkerThread
    override fun sendPacket(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback,
        sendPayloadFromSameThread: Boolean
    ): Boolean {
        var payloadTransferStarted = false
        try {
            // Prepare socket for the payload
            val server: ServerSocket? = if (np.hasPayload()) {
                openServerSocketOnFreePort(LanLinkProvider.PAYLOAD_TRANSFER_MIN_PORT).also {
                    val payloadTransferInfo = JSONObject()
                    payloadTransferInfo.put("port", it.localPort)
                    np.payloadTransferInfo = payloadTransferInfo
                }
            } else {
                null
            }

            // Send body of the network packet
            try {
                val writer: OutputStream = socket.outputStream
                writer.write(np.serialize().toByteArray(Charsets.UTF_8))
                writer.flush()
            } catch (e: Exception) {
                disconnect() // main socket is broken, disconnect
                server?.closeSafe()
                throw e
            }

            // Send payload
            if (server != null) {
                if (sendPayloadFromSameThread) {
                    payloadTransferStarted = true
                    sendPayload(np, callback, server)
                } else {
                    ThreadHelper.execute {
                        try {
                            sendPayload(np, callback, server)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Log.e(LOG_TAG, "Async sendPayload failed for packet of type " + np.type + ". The Plugin was NOT notified.")
                        }
                    }
                    payloadTransferStarted = true
                }
            }

            if (!np.isCanceled) {
                callback.onSuccess()
            }
            return true
        } catch (e: Exception) {
            callback.onFailure(e)
            return false
        } finally {
            // Close the payload if we never called sendPayload(). Otherwise it will close it.
            if (!payloadTransferStarted) {
                np.payload?.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun sendPayload(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback,
        server: ServerSocket
    ) {
        val payload = np.payload!!
        var payloadSocket: Socket? = null
        try {
            if (np.isCanceled) {
                return
            }

            val inputStream = payload.inputStream
                ?: throw IOException("Payload for packet ${np.type} has no InputStream")

            // Wait a maximum of 10 seconds for the other end to establish a connection with our socket, close it afterwards
            server.setSoTimeout(10 * 1000)

            payloadSocket = server.accept()

            // Convert to SSL if needed
            payloadSocket = convertToSslSocket(context, payloadSocket, deviceId,
                isDeviceTrusted = true,
                clientMode = false
            )

            // Doesn't need closing since closing payloadSocket also closes its stream
            val outputStream = payloadSocket.outputStream

            Log.i(LOG_TAG, "Beginning to send payload for ${np.type})")
            val buffer = ByteArray(4096)
            val size = np.payloadSize
            var bytesRead = 0
            var progress: Long = 0
            var timeSinceLastUpdate: Long = -1
            while (!np.isCanceled && (inputStream.read(buffer).also { bytesRead = it }) != -1) {
                progress += bytesRead
                outputStream.write(buffer, 0, bytesRead)
                if (size > 0) {
                    if (timeSinceLastUpdate + 500 < SystemClock.elapsedRealtime()) { //Report progress every half a second
                        val percent = ((100 * progress) / size)
                        callback.onPayloadProgressChanged(percent.toInt())
                        timeSinceLastUpdate = SystemClock.elapsedRealtime()
                    }
                }
            }
            outputStream.flush()
            Log.i(LOG_TAG, "Finished sending payload ($progress bytes written)")
        } catch (e: SocketTimeoutException) {
            Log.e(LOG_TAG, "Socket for payload in packet ${np.type} timed out. The other end didn't fetch the payload.")
            throw e
        } catch (e: CertificateException) {
            // The exception can be due to several causes. "Connection closed by peer" seems to be a common one.
            // If we could distinguish different cases we could react differently for some of them, but I haven't found how.
            Log.e(LOG_TAG, "Payload SSLSocket failed", e)
            throw IOException("Payload SSL socket failed", e)
        } catch (e: SSLHandshakeException) {
            Log.e(LOG_TAG, "Payload SSLSocket failed", e)
            throw IOException("Payload SSL socket failed", e)
        } finally {
            server.closeSafe()
            payloadSocket?.closeSafe()
            payload.close()
        }
    }

    private fun receivedNetworkPacket(np: NetworkPacket) {
        if (np.hasPayloadTransferInfo()) {
            var payloadSocket = Socket()
            try {
                val tcpPort = np.payloadTransferInfo.getInt("port")
                val deviceAddress = socket.getRemoteSocketAddress() as InetSocketAddress
                payloadSocket.connect(InetSocketAddress(deviceAddress.address, tcpPort))
                payloadSocket = convertToSslSocket(context, payloadSocket, deviceId,
                    isDeviceTrusted = true,
                    clientMode = true
                )
                np.payload = NetworkPacket.Payload(payloadSocket, np.payloadSize)
            } catch (e: Exception) {
                payloadSocket.closeSafe()
                Log.e(LOG_TAG, "Exception connecting to payload remote socket", e)
            }
        }

        packetReceived(np)
    }

    companion object {
        const val LOG_TAG = "LanLink"
        const val MAX_PACKET_SIZE: Int = 32 * 1024 * 1024
    }
}
