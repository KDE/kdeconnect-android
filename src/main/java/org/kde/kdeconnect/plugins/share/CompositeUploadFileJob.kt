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
import java.util.IdentityHashMap

/**
 * A type of [BackgroundJob] that sends Files to another device.
 *
 * We represent the individual upload requests as [NetworkPacket]s.
 *
 * Each packet should have a 'filename' property and a payload. If the payload is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * [addNetworkPackets].
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

    @Volatile
    private var isRunning: Boolean = false
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentFileName: String = ""
    private var currentFileNum: Int = 0
    private var currentHistoryId: String? = null
    private var updatePacketPending: Boolean = false
    private var totalSend: Long = 0
    private var prevProgressPercentage: Int = 0
    private val uploadNotification: UploadNotification = UploadNotification(device, id)

    private val lock = Any() // Use to protect concurrent access to the variables below

    @GuardedBy("lock")
    private val networkPacketList = mutableListOf<NetworkPacket>()
    private val historyIdsByPacket = IdentityHashMap<NetworkPacket, String>()

    private var currentNetworkPacket: NetworkPacket? = null
    private val sendPacketStatusCallback: SendPacketStatusCallback = SendPacketStatusCallback()

    @GuardedBy("lock")
    private var totalNumFiles: Int = 0

    @GuardedBy("lock")
    private var totalPayloadSize: Long = 0

    // Once set, this job can't be used anymore and addNetworkPackets() will reject new packets
    @GuardedBy("lock")
    private var finished: Boolean = false

    private val device: Device
        get() = requestInfo

    override fun run() {
        isRunning = true

        try {
            while (!isCancelled) {
                val packet = synchronized(lock) {
                    val next = networkPacketList.removeFirstOrNull()
                    if (next == null) {
                        finished = true
                        break
                    }
                    currentNetworkPacket = next
                    currentHistoryId = historyIdsByPacket[next]
                    next
                }

                currentFileName = packet.getString("filename")
                currentFileNum++
                updateCurrentHistory(TransferStatus.SENDING, 0)

                setProgress(prevProgressPercentage)

                addTotalsToNetworkPacket(packet)

                // We set sendPayloadFromSameThread to true so this call blocks until the payload
                // has been received by the other end, so payloads are sent one by one.
                if (!device.sendPacketBlocking(packet, sendPacketStatusCallback, true)) {
                    throw RuntimeException("Sending packet failed")
                }
            }

            if (isCancelled) {
                updateCurrentHistory(TransferStatus.FAILED, prevProgressPercentage)
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
            updateCurrentHistory(TransferStatus.FAILED, prevProgressPercentage)
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

            val pendingPackets = synchronized(lock) {
                finished = true
                val pending = networkPacketList.toList()
                for (networkPacket in pending) {
                    networkPacket.payload?.close()
                }
                networkPacketList.clear()
                pending
            }
            pendingPackets.forEach(::markFailed)
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

    /**
     * Adds either all the packets or none of them.
     *
     * @return false if the job has already finished and won't send these packets, so the caller
     * should use a new job instead
     */
    fun addNetworkPackets(networkPackets: List<NetworkPacket>): Boolean {
        synchronized(lock) {
            if (finished) {
                return false
            }

            for (networkPacket in networkPackets) {
                networkPacketList.add(networkPacket)

                totalNumFiles++
                val historyId = ShareTransferHistory.newId()
                historyIdsByPacket[networkPacket] = historyId
                ShareTransferHistory.upsert(
                    device.context,
                    ShareTransferRecord(
                        id = historyId,
                        fileName = networkPacket.getString("filename"),
                        size = networkPacket.payloadSize,
                        direction = TransferDirection.SENT,
                        status = TransferStatus.SENDING,
                        progress = 0,
                        deviceName = device.name,
                    ),
                )

                if (networkPacket.payloadSize >= 0) {
                    totalPayloadSize += networkPacket.payloadSize
                }
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
        return true
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
            updateCurrentHistory(TransferStatus.SENDING, percent)
            val send = totalSend + (payloadSize * (percent / 100f))
            val progress = ((send * 100) / totalPayloadSize).toInt()

            if (progress != prevProgressPercentage) {
                setProgress(progress)
                prevProgressPercentage = progress
            }
        }

        override fun onSuccess() {
            val payloadSize = currentNetworkPacket?.payloadSize ?: return
            updateCurrentHistory(TransferStatus.SENT, 100)
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

    private fun updateCurrentHistory(status: TransferStatus, progress: Int) {
        val historyId = currentHistoryId ?: return
        val packet = currentNetworkPacket ?: return
        ShareTransferHistory.upsert(
            device.context,
            ShareTransferRecord(
                id = historyId,
                fileName = packet.getString("filename"),
                size = packet.payloadSize,
                direction = TransferDirection.SENT,
                status = status,
                progress = progress.coerceIn(0, 100),
                deviceName = device.name,
            ),
        )
    }

    private fun markFailed(packet: NetworkPacket) {
        val historyId = historyIdsByPacket[packet] ?: return
        ShareTransferHistory.upsert(
            device.context,
            ShareTransferRecord(
                id = historyId,
                fileName = packet.getString("filename"),
                size = packet.payloadSize,
                direction = TransferDirection.SENT,
                status = TransferStatus.FAILED,
                progress = 0,
                deviceName = device.name,
            ),
        )
    }
}
