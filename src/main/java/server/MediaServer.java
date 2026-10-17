package server;

import common.FramedChannel;
import common.Request;
import common.RequestType;
import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;

/** Bounded concurrent sessions sharing one catalog. No queued unserved clients. */
public final class MediaServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private final Set<Socket> sessions = ConcurrentHashMap.newKeySet();
    private final RequestHandler handler;
    private final int timeoutMillis;
    private volatile boolean closed;

    public MediaServer(InetAddress bind, int port, Library library, int maxClients, int timeoutMillis) throws IOException {
        if (maxClients < 1 || maxClients > 64 || timeoutMillis < 100 || timeoutMillis > 600_000) {
            throw new IllegalArgumentException("Clients must be 1-64; idle read timeout 100-600000 ms");
        }
        this.timeoutMillis = timeoutMillis;
        handler = new RequestHandler(library);
        listener = new ServerSocket();
        try {
            listener.bind(new InetSocketAddress(bind, port), maxClients);
        } catch (IOException exception) {
            listener.close();
            throw exception;
        }
        workers = new ThreadPoolExecutor(maxClients, maxClients, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
    }

    public int port() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        while (!closed) {
            Socket socket;
            try {
                socket = listener.accept();
            } catch (SocketException exception) {
                if (closed) break;
                throw exception;
            }
            if (closed) { socket.close(); break; }
            sessions.add(socket);
            try {
                socket.setSoTimeout(timeoutMillis);
                workers.execute(() -> serveClient(socket));
            } catch (RejectedExecutionException | IOException exception) {
                sessions.remove(socket);
                socket.close();
            }
        }
    }

    private void serveClient(Socket socket) {
        try (socket) {
            FramedChannel channel = new FramedChannel(socket);
            while (!closed) {
                Request request = channel.receive(Request.class);
                channel.send(handler.handle(request));
                if (request.getType() == RequestType.DISCONNECT) break;
            }
        } catch (EOFException | SocketException ignored) {
            // Normal disconnect, overload close or server shutdown.
        } catch (IOException exception) {
            System.err.println("Session closed: " + exception.getClass().getSimpleName());
        } finally {
            sessions.remove(socket);
        }
    }

    @Override public void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket socket : sessions) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) System.err.println("Worker shutdown timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
