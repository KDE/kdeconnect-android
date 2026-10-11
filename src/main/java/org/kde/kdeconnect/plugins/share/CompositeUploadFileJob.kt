/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.os.Handler
import android.os.Looper
import androidx.annotation.GuardedBy
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect.plugins.share.SharePlugin.Companion.KEY_NUMBER_OF_FILES
import org.kde.kdeconnect.plugins.share.SharePlugin.Companion.KEY_TOTAL_PAYLOAD_SIZE
import org.kde.kdeconnect_tp.R
import java.util.ArrayList

/**
 * A type of [BackgroundJob] that sends Files to another device.
 *
 * We represent the individual upload requests as [NetworkPacket]s.
 *
 * Each packet should have a 'filename' property and a payload. If the payload is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * [addNetworkPacket].
 *
 * The I/O-part of this file sending is handled by
 * [Device.sendPacketBlocking].
 *
 * @see CompositeReceiveFileJob
 * @see Device.SendPacketStatusCallback
 */
class CompositeUploadFileJob(device: Device, callback: Callback<Unit>)
    : BackgroundJob<Device, Unit>(device, callback)
{

    private var isRunning: Boolean = false
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentFileName: String = ""
    private var currentFileNum: Int = 0
    private var updatePacketPending: Boolean = false
    private var totalSend: Long = 0
    private var prevProgressPercentage: Int = 0
    private val uploadNotification: UploadNotification = UploadNotification(device, id)

    private val lock = Any() // Use to protect concurrent access to the variables below

    @GuardedBy("lock")
    private val networkPacketList = mutableListOf<NetworkPacket>()

    private var currentNetworkPacket: NetworkPacket? = null
    private val sendPacketStatusCallback: SendPacketStatusCallback = SendPacketStatusCallback()

    @GuardedBy("lock")
    private var totalNumFiles: Int = 0

    @GuardedBy("lock")
    private var totalPayloadSize: Long = 0

    private val device: Device
        get() = requestInfo

    override fun run() {
        isRunning = true

        var done: Boolean
        synchronized(lock) {
            done = networkPacketList.isEmpty()
        }
        try {
            while (!done && !isCancelled) {
                val packet: NetworkPacket
                synchronized(lock) {
                    packet = networkPacketList.removeAt(0)
                    currentNetworkPacket = packet
                }

                currentFileName = packet.getString("filename")
                currentFileNum++

                setProgress(prevProgressPercentage)

                addTotalsToNetworkPacket(packet)

                // We set sendPayloadFromSameThread to true so this call blocks until the payload
                // has been received by the other end, so payloads are sent one by one.
                if (!device.sendPacketBlocking(packet, sendPacketStatusCallback, true)) {
                    throw RuntimeException("Sending packet failed")
                }

                synchronized(lock) {
                    done = networkPacketList.isEmpty()
                }
            }

            if (isCancelled) {
                uploadNotification.cancel()
            } else {
                uploadNotification.setFinished(
                    device.context.resources.getQuantityString(
                        R.plurals.sent_files_title,
                        currentFileNum,
                        device.name,
                        currentFileNum,
                    ),
                )
                uploadNotification.show()

                reportResult(Unit)
            }
        } catch (e: RuntimeException) {
            synchronized(lock) {
                val failedFiles = totalNumFiles - currentFileNum + 1
                uploadNotification.setFailed(
                    device.context.resources.getQuantityString(
                        R.plurals.send_files_fail_title,
                        failedFiles,
                        device.name,
                        failedFiles,
                        totalNumFiles,
                    ),
                )
            }

            uploadNotification.show()
            reportError(e)
        } finally {
            isRunning = false

            for (networkPacket in networkPacketList) {
                networkPacket.payload?.close()
            }
            networkPacketList.clear()
        }
    }

    private fun addTotalsToNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            networkPacket[KEY_NUMBER_OF_FILES] = totalNumFiles
            networkPacket[KEY_TOTAL_PAYLOAD_SIZE] = totalPayloadSize
        }
    }

    private fun setProgress(progress: Int) {
        synchronized(lock) {
            uploadNotification.setProgress(
                progress,
                device.context.resources.getQuantityString(
                    R.plurals.outgoing_files_text,
                    totalNumFiles,
                    currentFileName,
                    currentFileNum,
                    totalNumFiles,
                ),
            )
        }
        uploadNotification.show()
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            networkPacketList.add(networkPacket)

            totalNumFiles++

            if (networkPacket.payloadSize >= 0) {
                totalPayloadSize += networkPacket.payloadSize
            }

            uploadNotification.setTitle(
                device.context.resources.getQuantityString(
                    R.plurals.outgoing_file_title,
                    totalNumFiles,
                    totalNumFiles,
                    device.name,
                ),
            )

            // If we are already sending a file, update the notification right away to show the new number of files
            if (isRunning && currentFileNum > 0) {
                setProgress(prevProgressPercentage)
            }

            // Give SharePlugin some time to add more NetworkPackets
            if (isRunning && !updatePacketPending) {
                updatePacketPending = true
                handler.post { sendUpdatePacket() }
            }
        }
    }

    /**
     * Use this to send metadata ahead of all the other [networkPacketList] packets.
     */
    private fun sendUpdatePacket() {
        val np = NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST_UPDATE)

        synchronized(lock) {
            np[KEY_NUMBER_OF_FILES] = totalNumFiles
            np[KEY_TOTAL_PAYLOAD_SIZE] = totalPayloadSize
            updatePacketPending = false
        }

        device.sendPacket(np)
    }

    override fun cancel() {
        super.cancel()

        currentNetworkPacket?.cancel()
    }

    private inner class SendPacketStatusCallback : Device.SendPacketStatusCallback() {
        override fun onPayloadProgressChanged(percent: Int) {
            val payloadSize = currentNetworkPacket?.payloadSize ?: return
            val send = totalSend + (payloadSize * (percent / 100f))
            val progress = ((send * 100) / totalPayloadSize).toInt()

            if (progress != prevProgressPercentage) {
                setProgress(progress)
                prevProgressPercentage = progress
            }
        }

        override fun onSuccess() {
            val payloadSize = currentNetworkPacket?.payloadSize ?: return
            if (payloadSize == 0L) {
                synchronized(lock) {
                    if (networkPacketList.isEmpty()) {
                        setProgress(100)
                    }
                }
            } else {
                totalSend += payloadSize
            }
        }

        override fun onFailure(e: Throwable) {
            // Handled in the run() function when sendPacketBlocking returns false
        }
    }
}
