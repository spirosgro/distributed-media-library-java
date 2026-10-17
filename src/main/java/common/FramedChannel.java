package common;

import java.io.*;
import java.net.Socket;

/** Versioned, length-prefixed frames; each frame has a fresh serialization stream. */
public final class FramedChannel {
    public static final int MAGIC = 0x4D4C0001;
    private final DataInputStream input;
    private final DataOutputStream output;

    public FramedChannel(Socket socket) throws IOException {
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    public void send(Object value) throws IOException {
        byte[] encoded = ObjectCodec.encode(value);
        output.writeInt(MAGIC);
        output.writeInt(encoded.length);
        output.write(encoded);
        output.flush();
    }

    public <T> T receive(Class<T> expected) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Unsupported protocol version");
        int length = input.readInt();
        if (length <= 0 || length > ObjectCodec.MAX_BYTES) throw new IOException("Invalid frame length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        Object value = ObjectCodec.decode(bytes);
        if (!expected.isInstance(value)) throw new IOException("Unexpected message type");
        return expected.cast(value);
    }
}
