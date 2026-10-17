package client;

import common.FramedChannel;
import common.Request;
import common.Response;
import java.io.IOException;
import java.net.*;

/** One in-flight request per connection. Failed transport is never silently reused. */
public final class ClientConnection implements AutoCloseable {
    private Socket socket;
    private FramedChannel channel;

    public synchronized void connect(String host, int port) throws IOException {
        close();
        Socket candidate = new Socket();
        try {
            candidate.connect(new InetSocketAddress(host, port), 3000);
            candidate.setSoTimeout(5000);
            channel = new FramedChannel(candidate);
            socket = candidate;
        } catch (IOException | RuntimeException exception) {
            candidate.close();
            throw exception;
        }
    }

    public synchronized Response sendRequest(Request request) throws IOException {
        if (!isConnected()) throw new IOException("Connect to the server first");
        try {
            channel.send(request);
            return channel.receive(Response.class);
        } catch (IOException exception) {
            close();
            throw exception;
        }
    }

    public synchronized boolean isConnected() { return socket != null && !socket.isClosed(); }

    @Override public synchronized void close() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        socket = null;
        channel = null;
    }
}
