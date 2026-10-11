/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect.extensions.closeSafe
import org.kde.kdeconnect.helpers.FilesHelper
import org.kde.kdeconnect.helpers.MediaStoreHelper
import org.kde.kdeconnect_tp.R
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * A type of [BackgroundJob] that reads Files from another device.
 *
 * We receive the requests as [NetworkPacket]s.
 *
 * Each packet should have a 'filename' property and a payload. If the payload is missing,
 * we'll just create an empty file. You can add new packets anytime via
 * [addNetworkPacket].
 *
 * The I/O-part of this file reading is handled by [receiveFile].
 *
 * @see CompositeUploadFileJob
 */
class CompositeReceiveFileJob(device: Device, callBack: Callback<Unit>)
    : BackgroundJob<Device, Unit>(device, callBack)
{

    private val receiveNotification: ReceiveNotification = ReceiveNotification(device, id)
    private var currentNetworkPacket: NetworkPacket? = null
    private var currentFileName: String? = null
    private var currentFileNum: Int = 0
    private var totalReceived: Long = 0
    private var lastProgressTimeMillis: Long = 0
    private var prevProgressPercentage: Long = 0

    private val lock = Object() // Use to protect concurrent access to the variables below

    @GuardedBy("lock")
    private val networkPacketList = mutableListOf<NetworkPacket>()

    @GuardedBy("lock")
    private var totalNumFiles: Int = 0

    @GuardedBy("lock")
    private var totalPayloadSize: Long = 0

    var isRunning: Boolean = false
        private set

    private val device: Device
        get() = requestInfo

    fun updateTotals(numberOfFiles: Int, totalPayloadSize: Long) {
        synchronized(lock) {
            this.totalNumFiles = numberOfFiles
            this.totalPayloadSize = totalPayloadSize

            receiveNotification.setTitle(
                device.context.resources
                    .getQuantityString(R.plurals.incoming_file_title, totalNumFiles, totalNumFiles, device.name),
            )
            showNewTotalsIfRunning()

            lock.notifyAll()
        }
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            if (!networkPacketList.contains(networkPacket)) {
                networkPacketList.add(networkPacket)

                totalNumFiles = networkPacket.getInt(SharePlugin.KEY_NUMBER_OF_FILES, 1)
                totalPayloadSize = networkPacket.getLong(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE)

                receiveNotification.setTitle(
                    device.context.resources
                        .getQuantityString(R.plurals.incoming_file_title, totalNumFiles, totalNumFiles, device.name),
                )
                showNewTotalsIfRunning()

                // Wake up run() if it's waiting for the next packet
                lock.notifyAll()
            }
        }
    }

    override fun run() {
        var done: Boolean
        var outputStream: OutputStream? = null

        synchronized(lock) {
            done = networkPacketList.isEmpty()
        }

        try {
            var fileDocument: DocumentFile? = null

            isRunning = true

            while (!done && !isCancelled) {
                synchronized(lock) {
                    currentNetworkPacket = networkPacketList[0]
                }
                val packet = currentNetworkPacket!!
                currentFileName = packet.getString("filename", System.currentTimeMillis().toString())
                currentFileNum++

                setProgress(prevProgressPercentage.toInt())

                fileDocument = getDocumentFileFor(currentFileName!!, packet.getBoolean("open", false))

                if (packet.hasPayload()) {
                    outputStream = BufferedOutputStream(device.context.contentResolver.openOutputStream(fileDocument.uri), WRITE_BUFFER_SIZE)
                    val inputStream = packet.payload?.inputStream
                        ?: throw RuntimeException("Payload InputStream is null for $currentFileName")

                    val received = receiveFile(inputStream, outputStream)

                    packet.payload?.close()

                    outputStream.closeSafe()
                    outputStream = null

                    if (received != packet.payloadSize) {
                        fileDocument.delete()

                        if (!isCancelled) {
                            throw RuntimeException("Failed to receive: $currentFileName received:$received bytes, expected: ${packet.payloadSize} bytes")
                        }
                    } else {
                        publishFile(fileDocument, received)
                    }
                } else {
                    // TODO: Only set progress to 100 if this is the only file/packet to send
                    setProgress(100)
                    publishFile(fileDocument, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (packet.has("lastModified")) {
                        try {
                            val lastModified = packet.getLong("lastModified")
                            Files.setLastModifiedTime(Paths.get(fileDocument.uri.path!!), FileTime.fromMillis(lastModified))
                        } catch (e: Exception) {
                            Log.e("SharePlugin", "Can't set date on file")
                            e.printStackTrace()
                        }
                    }
                }

                synchronized(lock) {
                    networkPacketList.removeAt(0)

                    // Some senders only send the next packet after the previous payload has been fully transferred,
                    // so the list can be empty here. Wait for the next packet to arrive or for the timeout to expire.
                    val deadline = SystemClock.elapsedRealtime() + NEXT_PACKET_TIMEOUT_MILLIS
                    while (networkPacketList.isEmpty() && !isCancelled) {
                        // elapsedRealtime is monotonic, unlike currentTimeMillis() which changes when the time changes
                        val remainingMillis = deadline - SystemClock.elapsedRealtime()
                        if (remainingMillis <= 0) { // Note wait(0) would wait forever
                            break
                        }
                        try {
                            lock.wait(remainingMillis)
                        } catch (_: InterruptedException) {
                            // Job was cancelled
                            Thread.currentThread().interrupt()
                            break
                        }
                    }

                    if (!isCancelled && currentFileNum < totalNumFiles && networkPacketList.isEmpty()) {
                        throw RuntimeException("Failed to receive ${totalNumFiles - currentFileNum + 1} files")
                    }

                    done = networkPacketList.isEmpty()
                }
            }

            isRunning = false

            if (isCancelled) {
                receiveNotification.cancel()
                return
            }

            val numFiles: Int = synchronized(lock) {
                totalNumFiles
            }

            if (numFiles == 1 && currentNetworkPacket?.getBoolean("open", false) == true && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                receiveNotification.cancel()
                fileDocument?.let { openFile(it) }
            } else {
                // Update the notification and allow to open the file from it
                receiveNotification.setFinished(device.context.resources.getQuantityString(R.plurals.received_files_title, numFiles, device.name, numFiles))

                if (totalNumFiles == 1 && fileDocument != null) {
                    receiveNotification.setURI(fileDocument.uri, fileDocument.type, fileDocument.name)
                }

                receiveNotification.show()
            }
            reportResult(Unit)
        } catch (_: ActivityNotFoundException) {
            receiveNotification.setFinished(device.context.getString(R.string.no_app_for_opening))
            receiveNotification.show()
        } catch (e: Exception) {
            isRunning = false

            Log.e("Shareplugin", "Error receiving file", e)

            val failedFiles: Int = synchronized(lock) {
                totalNumFiles - currentFileNum + 1
            }

            receiveNotification.setFailed(device.context.resources.getQuantityString(R.plurals.received_files_fail_title, failedFiles, device.name, failedFiles, totalNumFiles))
            receiveNotification.show()
            reportError(e)
        } finally {
            closeAllInputStreams()
            networkPacketList.clear()
            outputStream?.closeSafe()
        }
    }

    private fun getDocumentFileFor(filename: String, open: Boolean): DocumentFile {
        val destinationFolderDocument: DocumentFile

        // If the file should be opened immediately store it in the standard location to avoid the FileProvider trouble (See ReceiveNotification::setURI)
        if (open || !ShareSettingsFragment.isCustomDestinationEnabled(device.context)) {
            val defaultPath = ShareSettingsFragment.getDefaultDestinationDirectory().absolutePath
            destinationFolderDocument = DocumentFile.fromFile(File(defaultPath))
        } else {
            destinationFolderDocument = ShareSettingsFragment.getDestinationDirectory(device.context)
        }

        val filenameToUse = FilesHelper.findValidNonExistingFileName(destinationFolderDocument, filename)

        val fileDocument = destinationFolderDocument.createFile("*/*", filenameToUse)
            ?: throw RuntimeException(device.context.getString(R.string.cannot_create_file, filenameToUse))

        return fileDocument
    }

    private fun receiveFile(input: InputStream, output: OutputStream): Long {
        val data = ByteArray(READ_BUFFER_SIZE)
        var count: Int
        var received: Long = 0

        while (input.read(data).also { count = it } >= 0 && !isCancelled) {
            received += count.toLong()
            totalReceived += count.toLong()

            output.write(data, 0, count)

            val progressPercentage: Long = synchronized(lock) {
                totalReceived * 100 / totalPayloadSize
            }
            val curTimeMillis = System.currentTimeMillis()

            if (progressPercentage != prevProgressPercentage &&
                (progressPercentage == 100L || curTimeMillis - lastProgressTimeMillis >= 500)
            ) {
                prevProgressPercentage = progressPercentage
                lastProgressTimeMillis = curTimeMillis
                setProgress(progressPercentage.toInt())
            }
        }

        output.flush()

        return received
    }

    private fun closeAllInputStreams() {
        for (np in networkPacketList) {
            np.payload?.close()
        }
    }

    private fun showNewTotalsIfRunning() {
        // If we are already receiving a file, update the notification right away to show the new number of files
        if (isRunning && currentFileNum > 0) {
            setProgress(prevProgressPercentage.toInt())
        }
    }

    private fun setProgress(progress: Int) {
        synchronized(lock) {
            receiveNotification.setProgress(
                progress,
                device.context.resources
                    .getQuantityString(R.plurals.incoming_files_text, totalNumFiles, currentFileName, currentFileNum, totalNumFiles),
            )
        }
        receiveNotification.show()
    }

    private fun publishFile(fileDocument: DocumentFile, size: Long) {
        if (!ShareSettingsFragment.isCustomDestinationEnabled(device.context)) {
            Log.i("SharePlugin", "Adding to downloads")
            val manager = ContextCompat.getSystemService(
                device.context,
                DownloadManager::class.java,
            )
            manager?.addCompletedDownload(fileDocument.uri.lastPathSegment, device.name, true, fileDocument.type, fileDocument.uri.path, size, false)
        } else {
            // Make sure it is added to the Android Gallery anyway
            Log.i("SharePlugin", "Adding to gallery")
            MediaStoreHelper.indexFile(device.context, fileDocument.uri)
        }
    }

    private fun openFile(fileDocument: DocumentFile) {
        val mimeType = FilesHelper.getMimeTypeFromFile(fileDocument.name)
        val intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Nougat and later require "content://" uris instead of "file://" uris
            val file = File(fileDocument.uri.path ?: "")
            val contentUri = FileProvider.getUriForFile(device.context, "org.kde.kdeconnect_tp.fileprovider", file)
            intent.setDataAndType(contentUri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            intent.setDataAndType(fileDocument.uri, mimeType)
        }

        // Open files for KDE Itinerary explicitly because Android's activity resolution sucks
        if (fileDocument.name?.endsWith(".itinerary") == true) {
            intent.setClassName("org.kde.itinerary", "org.kde.itinerary.Activity")
        }

        device.context.startActivity(intent)
    }

    companion object {
        // How long to wait for the next file of the batch before failing the batch
        private const val NEXT_PACKET_TIMEOUT_MILLIS: Long = 1000
        private const val WRITE_BUFFER_SIZE = 1024 * 1024
        private const val READ_BUFFER_SIZE = 256 * 1024
    }
}
