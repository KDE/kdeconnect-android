package org.kde.kdeconnect.Backends.BluetoothBackend;

import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ConnectionMultiplexer implements Closeable {
    private static final UUID DEFAULT_CHANNEL = UUID.fromString("a0d0aaf4-1072-4d81-aa35-902a954b1266");
    private static final int BUFFER_SIZE = 4096;

    private static final class ChannelInputStream extends InputStream implements Closeable {
        Channel channel;

        ChannelInputStream(Channel channel) {
            this.channel = channel;
        }

        @Override
        public int available() {
            return channel.available();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public int read() {
            byte[] b = new byte[1];
            if (read(b, 0, 1) == -1) {
                return -1;
            } else {
                return b[0];
            }
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return channel.read(b, off, len);
        }

        @Override
        public int read(byte[] b) {
            return read(b, 0, b.length);
        }
    }

    private static final class ChannelOutputStream extends OutputStream implements Closeable {
        Channel channel;

        ChannelOutputStream(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        @Override
        public void flush() throws IOException {
            channel.flush();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            channel.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            byte[] data = new byte[1];
            data[0] = (byte) b;
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }
    }

    private static final class Channel implements Closeable {
        ConnectionMultiplexer multiplexer;
        UUID id;
        ByteBuffer read_buffer = ByteBuffer.allocate(BUFFER_SIZE);
        final Object lock = new Object();
        boolean open = true;
        int requestedReadAmount = 0; //Number of times we requested some bytes from the channel
        int freeWriteAmount = 0; //Number of times we can safely send bytes over the channel

        Channel(ConnectionMultiplexer multiplexer, UUID id) {
            this.multiplexer = multiplexer;
            this.id = id;
        }

        int available() {
            synchronized (lock) {
                return read_buffer.position();
            }
        }

        public int read(byte[] b, int off, int len) {
            if (len == 0) return 0;

            while (true) {
                boolean makeRequest;
                synchronized (lock) {
                    if (read_buffer.position() >= len) {
                        read_buffer.flip();
                        read_buffer.get(b, off, len);
                        read_buffer.compact();

                        //TODO: non-blocking (opportunistic) read request
                        return len;
                    } else if (read_buffer.position() > 0) {
                        int numread = read_buffer.position();
                        read_buffer.flip();
                        read_buffer.get(b, off, numread);
                        read_buffer.compact();

                        //TODO: non-blocking (opportunistic) read request
                        return numread;
                    }

                    if (!open) return -1;
                    makeRequest = requestedReadAmount < BUFFER_SIZE;
                }

                if (makeRequest) {
                    multiplexer.readRequest(id);
                }

                synchronized (lock) {
                    if (!open) return -1;
                    if (read_buffer.position() > 0) continue;

                    try {
                        lock.wait();
                    } catch (Exception ignored) {}
                }
            }
        }

        @Override
        public void close() throws IOException {
            flush();
            synchronized (lock) {
                open = false;
                read_buffer.clear();
                lock.notifyAll();
            }
            multiplexer.closeChannel(id);
        }

        void doClose() {
            synchronized (lock) {
                open = false;
                lock.notifyAll();
            }
        }

        public void write(byte[] data, int off, int len) throws IOException {
            while (len > 0) {
                synchronized (lock) {
                    while (true) {
                        if (!open) throw new IOException("Connection closed!");

                        if (freeWriteAmount == 0) {
                            try {
                                lock.wait();
                            } catch (Exception ignored) {}
                        } else {
                            break;
                        }
                    }
                }

                int num_written = multiplexer.writeRequest(id, data, off, len);
                off += num_written;
                len -= num_written;
            }
        }

        void flush() throws IOException {
            multiplexer.flush();
        }
    }

    private BluetoothSocket socket;
    private Map<UUID, Channel> channels = new HashMap<>();
    private final Object lock = new Object();
    private boolean open = true;
    private boolean receivedProtocolVersion = false;

    private static final byte MESSAGE_PROTOCOL_VERSION = 0; //Negotiate the protocol version
    private static final byte MESSAGE_OPEN_CHANNEL = 1; //Open a new channel
    private static final byte MESSAGE_CLOSE_CHANNEL = 2; //Close a channel
    private static final byte MESSAGE_READ = 3; //Request some bytes from a channel
    private static final byte MESSAGE_WRITE = 4; //Write some bytes to a channel

    public ConnectionMultiplexer(BluetoothSocket socket) throws IOException {
        this.socket = socket;
        channels.put(DEFAULT_CHANNEL, new Channel(this, DEFAULT_CHANNEL));

        sendProtocolVersion();

        new ListenThread(socket).start();
    }

    private void sendProtocolVersion() throws IOException {
        byte[] data = new byte[23];
        ByteBuffer message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        message.put(MESSAGE_PROTOCOL_VERSION);
        message.putShort((short) 4);
        message.position(19);
        message.putShort((short) 1);
        message.putShort((short) 1);

        socket.getOutputStream().write(data);
    }

    private void handleException(IOException e) {
        synchronized (lock) {
            open = false;
            for (Channel channel : channels.values()) {
                channel.doClose();
            }
            channels.clear();
            if (socket.isConnected()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void closeChannel(UUID id) {
        synchronized (lock) {
            if (channels.containsKey(id)) {
                channels.remove(id);

                byte[] data = new byte[19];
                ByteBuffer message = ByteBuffer.wrap(data);
                message.order(ByteOrder.BIG_ENDIAN);
                message.put(MESSAGE_CLOSE_CHANNEL);
                message.putShort((short) 0);
                message.putLong(id.getMostSignificantBits());
                message.putLong(id.getLeastSignificantBits());

                try {
                    socket.getOutputStream().write(data);
                } catch (IOException e) {
                    handleException(e);
                }
            }
        }
    }

    private void readRequest(UUID id) {
        synchronized (lock) {
            Channel channel = channels.get(id);
            if (channel == null) return;

            byte[] data = new byte[21];
            synchronized (channel.lock) {
                if (!channel.open) return;
                if (channel.read_buffer.position() + channel.requestedReadAmount >= BUFFER_SIZE) return;
                int amount = BUFFER_SIZE - channel.read_buffer.position() - channel.requestedReadAmount;

                ByteBuffer message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                message.put(MESSAGE_READ);
                message.putShort((short) 2);
                message.putLong(id.getMostSignificantBits());
                message.putLong(id.getLeastSignificantBits());
                message.putShort((short) amount);
                channel.requestedReadAmount += amount;

                try {
                    socket.getOutputStream().write(data);
                } catch (IOException e) {
                    handleException(e);
                }
                channel.lock.notifyAll();
            }
        }
    }

    private int writeRequest(UUID id, byte[] write_data, int off, int write_len) throws IOException {
        synchronized (lock) {
            Channel channel = channels.get(id);
            if (channel == null) return 0;

            byte[] data = new byte[19 + BUFFER_SIZE];
            int length;
            synchronized (channel.lock) {
                if (!channel.open) return 0;
                if (channel.freeWriteAmount == 0) return 0;

                length = channel.freeWriteAmount;
                if (write_len < length) {
                    length = write_len;
                }

                ByteBuffer message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                message.put(MESSAGE_WRITE);
                //Convert length to signed short
                short lengthShort;
                if (length >= 0x10000) {
                    throw new IOException("Invalid buffer size, too large!");
                } else if (length >= 0x8000) {
                    lengthShort = (short) (-0x10000 + length);
                } else {
                    lengthShort = (short) length;
                }
                message.putShort(lengthShort);
                message.putLong(id.getMostSignificantBits());
                message.putLong(id.getLeastSignificantBits());

                message.put(write_data, off, length);

                channel.freeWriteAmount -= length;
                channel.lock.notifyAll();
            }

            try {
                socket.getOutputStream().write(data, 0, 19 + length);
            } catch (IOException e) {
                handleException(e);
            }
            return length;
        }
    }

    private void flush() throws IOException {
        synchronized (lock) {
            if (!open) return;
            socket.getOutputStream().flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (socket == null) {
            return;
        }
        socket.close();
        socket = null;
        for (Channel channel : channels.values()) {
            channel.doClose();
        }
        channels.clear();
    }

    public UUID newChannel() throws IOException {
        UUID id = UUID.randomUUID();
        synchronized (lock) {
            byte[] data = new byte[19];
            ByteBuffer message = ByteBuffer.wrap(data);
            message.order(ByteOrder.BIG_ENDIAN);
            message.put(MESSAGE_OPEN_CHANNEL);
            message.putShort((short) 0);
            message.putLong(id.getMostSignificantBits());
            message.putLong(id.getLeastSignificantBits());

            try {
                socket.getOutputStream().write(data);
            } catch (IOException e) {
                handleException(e);
                throw e;
            }
            channels.put(id, new Channel(this, id));
        }
        return id;
    }

    public InputStream getDefaultInputStream() throws IOException {
        return getChannelInputStream(DEFAULT_CHANNEL);
    }

    public OutputStream getDefaultOutputStream() throws IOException {
        return getChannelOutputStream(DEFAULT_CHANNEL);
    }

    public InputStream getChannelInputStream(UUID id) throws IOException {
        synchronized (lock) {
            Channel channel = channels.get(id);
            if (channel == null) throw new IOException("Invalid channel!");
            return new ChannelInputStream(channel);
        }
    }

    public OutputStream getChannelOutputStream(UUID id) throws IOException {
        synchronized (lock) {
            Channel channel = channels.get(id);
            if (channel == null) throw new IOException("Invalid channel!");
            return new ChannelOutputStream(channel);
        }
    }

    private final class ListenThread extends Thread {
        InputStream input;
        OutputStream output;

        ListenThread(BluetoothSocket socket) throws IOException {
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        private void read_buffer(byte[] buffer, int len) throws IOException {
            int num_read = 0;
            while (num_read < len) {
                int count = input.read(buffer, num_read, len - num_read);
                if (count == -1) {
                    throw new IOException("Couldn't read enough bytes!");
                }

                num_read += count;
            }
        }

        private void read_message() throws IOException {
            byte[] data = new byte[BUFFER_SIZE];
            read_buffer(data, 19);
            ByteBuffer message = ByteBuffer.wrap(data, 0, 19).order(ByteOrder.BIG_ENDIAN);
            byte type = message.get();
            int length = message.getShort();
            //signed short -> unsigned short (as int) conversion
            if (length < 0) length += 0x10000;
            long channel_id_msb = message.getLong();
            long channel_id_lsb = message.getLong();
            UUID channel_id = new UUID(channel_id_msb, channel_id_lsb);

            if (!receivedProtocolVersion && type != MESSAGE_PROTOCOL_VERSION) {
                throw new IOException("Did not receive protocol version message!");
            }

            if (type == MESSAGE_OPEN_CHANNEL) {
                synchronized (lock) {
                    channels.put(channel_id, new Channel(ConnectionMultiplexer.this, channel_id));
                }
            } else if (type == MESSAGE_CLOSE_CHANNEL) {
                synchronized (lock) {
                    Channel channel = channels.get(channel_id);
                    if (channel == null) return;
                    channels.remove(channel_id);

                    channel.doClose();
                }
            } else if (type == MESSAGE_READ) {
                if (length != 2) {
                    throw new IOException("Message length is invalid for 'MESSAGE_READ'!");
                }
                read_buffer(data, 2);
                int amount = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).getShort();
                //signed short -> unsigned short (as int) conversion
                if (amount < 0) amount += 0x10000;

                synchronized (lock) {
                    Channel channel = channels.get(channel_id);
                    if (channel == null) return;

                    synchronized (channel.lock) {
                        channel.freeWriteAmount += amount;
                        channel.lock.notifyAll();
                    }
                }
            } else if (type == MESSAGE_WRITE) {
                if (length > BUFFER_SIZE) {
                    throw new IOException("Message length is bigger than read size!");
                }
                read_buffer(data, length);

                synchronized (lock) {
                    Channel channel = channels.get(channel_id);
                    if (channel == null) return;
                    synchronized (channel.lock) {
                        if (channel.requestedReadAmount < length) {
                            throw new IOException("No outstanding read requests of this length!");
                        }
                        channel.requestedReadAmount -= length;
                        if (channel.read_buffer.position() + length > BUFFER_SIZE) {
                            throw new IOException("Shouldn't be getting more data when the buffer is too full!");
                        }
                        channel.read_buffer.put(data, 0, length);
                        channel.lock.notifyAll();
                    }
                }
            } else if (type == MESSAGE_PROTOCOL_VERSION) {
                //Allow more than 4 bytes data, for future extensibility
                if (length < 4) {
                    throw new IOException("Message length is invalid for 'MESSAGE_PROTOCOL_VERSION'!");
                }
                //We might need a larger buffer to read this
                if (length > data.length) {
                    data = new byte[1 << 16];
                }
                read_buffer(data, length);

                //Check remote endpoint protocol version
                int minimum_version = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).getShort();
                //signed short -> unsigned short (as int) conversion
                if (minimum_version < 0) minimum_version += 0x10000;
                int maximum_version = ByteBuffer.wrap(data, 2, 2).order(ByteOrder.BIG_ENDIAN).getShort();
                //signed short -> unsigned short (as int) conversion
                if (maximum_version < 0) maximum_version += 0x10000;

                if (minimum_version > 1 || maximum_version < 1) {
                    throw new IOException("Unsupported protocol version " + minimum_version + " - " + maximum_version + "!");
                }
                //We now support receiving other messages
                receivedProtocolVersion = true;
            } else {
                throw new IOException("Invalid message type " + (int) type);
            }
        }

        @Override
        public void run() {
            while (true) {
                synchronized (lock) {
                    if (!open) return;
                }
                try {
                    read_message();
                } catch (IOException e) {
                    handleException(e);
                    return;
                }
            }
        }
    }
}
