/*
 * SPDX-FileCopyrightText: 2019 Matthijs Tijink <matthijstijink@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConnectionMultiplexer(socket: BluetoothSocket) : Closeable {
    private class ChannelInputStream(val channel: Channel) : InputStream(), Closeable {
        override fun available(): Int {
            return channel.available()
        }

        @Throws(IOException::class)
        override fun close() {
            channel.close()
        }

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) {
                -1
            } else {
                b[0].toInt()
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return channel.read(b, off, len)
        }

        override fun read(b: ByteArray): Int {
            return read(b, 0, b.size)
        }
    }

    private class ChannelOutputStream(val channel: Channel) : OutputStream(), Closeable {
        @Throws(IOException::class)
        override fun close() {
            channel.close()
        }

        @Throws(IOException::class)
        override fun flush() {
            channel.flush()
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            channel.write(b, off, len)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            val data = ByteArray(1)
            data[0] = b.toByte()
            write(data, 0, 1)
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }
    }

    private class Channel(val multiplexer: ConnectionMultiplexer, val id: UUID) : Closeable {
        val readBuffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        val lock = ReentrantLock()
        var lockCondition: Condition = lock.newCondition()

        var open = true
        var requestedReadAmount = 0 //Number of times we requested some bytes from the channel
        var freeWriteAmount = 0 //Number of times we can safely send bytes over the channel
        fun available(): Int {
            lock.withLock { return readBuffer.position() }
        }

        fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (true) {
                var makeRequest: Boolean
                lock.withLock {
                    if (readBuffer.position() >= len) {
                        readBuffer.flip()
                        readBuffer[b, off, len]
                        readBuffer.compact()

                        //TODO: non-blocking (opportunistic) read request
                        return len
                    } else if (readBuffer.position() > 0) {
                        val numberRead = readBuffer.position()
                        readBuffer.flip()
                        readBuffer[b, off, numberRead]
                        readBuffer.compact()

                        //TODO: non-blocking (opportunistic) read request
                        return numberRead
                    }
                    if (!open) return -1
                    makeRequest = requestedReadAmount < BUFFER_SIZE
                }
                if (makeRequest) {
                    multiplexer.readRequest(id)
                }
                lock.withLock {
                    if (!open) return -1
                    if (readBuffer.position() <= 0) {
                        try {
                            lockCondition.await()
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun close() {
            flush()
            lock.withLock {
                if (!open) return
                open = false
                readBuffer.clear()
                lockCondition.signalAll()
            }
            multiplexer.closeChannel(id)
        }

        fun doClose() {
            lock.withLock {
                open = false
                lockCondition.signalAll()
            }
        }

        @Throws(IOException::class)
        fun write(data: ByteArray, off: Int, len: Int) {
            var offset = off
            var length = len
            while (length > 0) {
                lock.withLock {
                    while (true) {
                        if (!open) throw IOException("Connection closed!")
                        if (freeWriteAmount == 0) {
                            try {
                                lockCondition.await()
                            } catch (_: Exception) {
                            }
                        } else {
                            break
                        }
                    }
                }
                val numWritten = multiplexer.writeRequest(id, data, offset, length)
                offset += numWritten
                length -= numWritten
            }
        }

        @Throws(IOException::class)
        fun flush() {
            multiplexer.flush()
        }
    }

    private val socket: BluetoothSocket
    private val channels: MutableMap<UUID, Channel> = HashMap()
    private val channelsLock = ReentrantLock()
    private var open = true
    private var receivedProtocolVersion = false

    init {
        this.socket = socket
        channels[DEFAULT_CHANNEL] = Channel(this, DEFAULT_CHANNEL)
        sendProtocolVersion()
        execute(ListenRunnable(socket))
    }

    @Throws(IOException::class)
    private fun sendProtocolVersion() {
        val data = ByteArray(23)
        val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        message.put(MESSAGE_PROTOCOL_VERSION)
        message.putShort(4.toShort())
        message.position(19)
        message.putShort(1.toShort())
        message.putShort(1.toShort())
        socket.outputStream.write(data)
    }

    private fun handleException(@Suppress("UNUSED_PARAMETER") ignored: Exception) {
        channelsLock.withLock {
            open = false
            for (channel in channels.values) {
                channel.doClose()
            }
            channels.clear()
            if (socket.isConnected) {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun closeChannel(id: UUID) {
        channelsLock.withLock {
            if (channels.containsKey(id)) {
                channels.remove(id)
                val data = ByteArray(19)
                val message = ByteBuffer.wrap(data)
                message.order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_CLOSE_CHANNEL)
                message.putShort(0.toShort())
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                try {
                    socket.outputStream.write(data)
                } catch (e: IOException) {
                    handleException(e)
                }
            }
        }
    }

    private fun readRequest(id: UUID) {
        channelsLock.withLock {
            val channel = channels[id] ?: return
            val data = ByteArray(21)
            channel.lock.withLock {
                if (!channel.open) return
                if (channel.readBuffer.position() + channel.requestedReadAmount >= BUFFER_SIZE) return
                val amount = BUFFER_SIZE - channel.readBuffer.position() - channel.requestedReadAmount
                val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_READ)
                message.putShort(2.toShort())
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                message.putShort(amount.toShort())
                channel.requestedReadAmount += amount
                try {
                    socket.outputStream.write(data)
                } catch (e: IOException) {
                    handleException(e)
                } catch (e: NullPointerException) {
                    handleException(e)
                }
                channel.lockCondition.signalAll()
            }
        }
    }

    @Throws(IOException::class)
    private fun writeRequest(id: UUID, writeData: ByteArray, off: Int, writeLen: Int): Int {
        channelsLock.withLock {
            val channel = channels[id] ?: return 0
            val data = ByteArray(19 + BUFFER_SIZE)
            var length: Int
            channel.lock.withLock {
                if (!channel.open) return 0
                if (channel.freeWriteAmount == 0) return 0
                length = channel.freeWriteAmount
                if (writeLen < length) {
                    length = writeLen
                }
                val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_WRITE)
                //Convert length to signed short
                val lengthShort: Short = if (length >= 0x10000) {
                    throw IOException("Invalid buffer size, too large!")
                } else if (length >= 0x8000) {
                    (-0x10000 + length).toShort()
                } else {
                    length.toShort()
                }
                message.putShort(lengthShort)
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                message.put(writeData, off, length)
                channel.freeWriteAmount -= length
                channel.lockCondition.signalAll()
            }
            try {
                socket.outputStream.write(data, 0, 19 + length)
            } catch (e: IOException) {
                handleException(e)
            }
            return length
        }
    }

    @Throws(IOException::class)
    private fun flush() {
        channelsLock.withLock {
            if (!open) return
            socket.outputStream.flush()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        channelsLock.withLock {
            socket.close()
            for (channel in channels.values) {
                channel.doClose()
            }
            channels.clear()
        }
    }

    @Throws(IOException::class)
    fun newChannel(): UUID {
        val id = UUID.randomUUID()
        channelsLock.withLock {
            val data = ByteArray(19)
            val message = ByteBuffer.wrap(data)
            message.order(ByteOrder.BIG_ENDIAN)
            message.put(MESSAGE_OPEN_CHANNEL)
            message.putShort(0.toShort())
            message.putLong(id.mostSignificantBits)
            message.putLong(id.leastSignificantBits)
            try {
                socket.outputStream.write(data)
            } catch (e: IOException) {
                handleException(e)
                throw e
            }
            channels.put(id, Channel(this, id))
        }
        return id
    }

    @get:Throws(IOException::class)
    val defaultInputStream: InputStream
        get() = getChannelInputStream(DEFAULT_CHANNEL)

    @get:Throws(IOException::class)
    val defaultOutputStream: OutputStream
        get() = getChannelOutputStream(DEFAULT_CHANNEL)

    @Throws(IOException::class)
    fun getChannelInputStream(id: UUID): InputStream {
        channelsLock.withLock {
            val channel = channels[id] ?: throw IOException("Invalid channel!")
            return ChannelInputStream(channel)
        }
    }

    @Throws(IOException::class)
    fun getChannelOutputStream(id: UUID): OutputStream {
        channelsLock.withLock {
            val channel = channels[id] ?: throw IOException("Invalid channel!")
            return ChannelOutputStream(channel)
        }
    }

    private inner class ListenRunnable(socket: BluetoothSocket) : Runnable {
        var input: InputStream = socket.inputStream
        var output: OutputStream = socket.outputStream

        @Throws(IOException::class)
        private fun readBuffer(buffer: ByteArray, len: Int) {
            var numRead = 0
            while (numRead < len) {
                val count = input.read(buffer, numRead, len - numRead)
                if (count == -1) {
                    throw IOException("Couldn't read enough bytes!")
                }
                numRead += count
            }
        }

        fun byteArrayToHexString(bytes: ByteArray): String {
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("0x%02x ", b.toInt() and 0xff))
            }
            return sb.toString()
        }

        @Throws(IOException::class)
        private fun readMessage() {
            var data = ByteArray(BUFFER_SIZE)
            readBuffer(data, 19)
            val message = ByteBuffer.wrap(data, 0, 19).order(ByteOrder.BIG_ENDIAN)
            val type = message.get()
            var length = message.short.toInt()
            //signed short -> unsigned short (as int) conversion
            if (length < 0) length += 0x10000
            val channelIdMostSigBits = message.long
            val channelIdLeastSigBits = message.long
            val channelId = UUID(channelIdMostSigBits, channelIdLeastSigBits)
            if (!receivedProtocolVersion && type != MESSAGE_PROTOCOL_VERSION) {
                Log.w("ConnectionMultiplexer", "Received invalid message '$message'")
                Log.w("ConnectionMultiplexer", "'data_buffer:(" + byteArrayToHexString(data) + ") ")
                Log.w("ConnectionMultiplexer", "as string: '$data' ")

                throw IOException("Did not receive protocol version message!")
            }
            when (type) {
                MESSAGE_OPEN_CHANNEL -> {
                    channelsLock.withLock {
                        channels.put(channelId, Channel(this@ConnectionMultiplexer, channelId))
                    }
                }
                MESSAGE_CLOSE_CHANNEL -> {
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channels.remove(channelId)
                        channel.doClose()
                    }
                }
                MESSAGE_READ -> {
                    if (length != 2) {
                        throw IOException("Message length is invalid for 'MESSAGE_READ'!")
                    }
                    readBuffer(data, 2)
                    var amount = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (amount < 0) amount += 0x10000
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channel.lock.withLock {
                            channel.freeWriteAmount += amount
                            channel.lockCondition.signalAll()
                        }
                    }
                }
                MESSAGE_WRITE -> {
                    if (length > BUFFER_SIZE) {
                        throw IOException("Message length is bigger than read size!")
                    }
                    readBuffer(data, length)
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channel.lock.withLock {
                            if (channel.requestedReadAmount < length) {
                                throw IOException("No outstanding read requests of this length!")
                            }
                            channel.requestedReadAmount -= length
                            if (channel.readBuffer.position() + length > BUFFER_SIZE) {
                                throw IOException("Shouldn't be getting more data when the buffer is too full!")
                            }
                            channel.readBuffer.put(data, 0, length)
                            channel.lockCondition.signalAll()
                        }
                    }
                }
                MESSAGE_PROTOCOL_VERSION -> {
                    //Allow more than 4 bytes data, for future extensibility
                    if (length < 4) {
                        throw IOException("Message length is invalid for 'MESSAGE_PROTOCOL_VERSION'!")
                    }
                    //We might need a larger buffer to read this
                    if (length > data.size) {
                        data = ByteArray(1 shl 16)
                    }
                    readBuffer(data, length)

                    //Check remote endpoint protocol version
                    var minimumVersion = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (minimumVersion < 0) minimumVersion += 0x10000
                    var maximumVersion = ByteBuffer.wrap(data, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (maximumVersion < 0) maximumVersion += 0x10000
                    if (minimumVersion > 1 || maximumVersion < 1) {
                        throw IOException("Unsupported protocol version $minimumVersion - $maximumVersion!")
                    }
                    //We now support receiving other messages
                    receivedProtocolVersion = true
                }
                else -> {
                    throw IOException("Invalid message type " + type.toInt())
                }
            }
        }

        override fun run() {
            while (true) {
                channelsLock.withLock {
                    if (!open) {
                        Log.w("ConnectionMultiplexer", "connection not open, returning")
                        return
                    }
                }
                try {
                    readMessage()
                } catch (e: IOException) {
                    Log.w("ConnectionMultiplexer", "run caught IOException", e)
                    handleException(e)
                    return
                }
            }
        }
    }

    companion object {
        private val DEFAULT_CHANNEL = UUID.fromString("a0d0aaf4-1072-4d81-aa35-902a954b1266")
        private const val BUFFER_SIZE = 4096
        private const val MESSAGE_PROTOCOL_VERSION: Byte = 0 //Negotiate the protocol version
        private const val MESSAGE_OPEN_CHANNEL: Byte = 1 //Open a new channel
        private const val MESSAGE_CLOSE_CHANNEL: Byte = 2 //Close a channel
        private const val MESSAGE_READ: Byte = 3 //Request some bytes from a channel
        private const val MESSAGE_WRITE: Byte = 4 //Write some bytes to a channel
    }
}
