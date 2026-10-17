package server;

import java.net.InetAddress;
import java.nio.file.Path;

/** Launch a local teaching server; persistence is explicit, never automatic. */
public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] args) throws Exception {
        if (args.length > 3 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println("Usage: java -cp build/media-library.jar server.ServerMain [port=5000] [data-directory=data] [bind=127.0.0.1]");
            return;
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        Path data = Path.of(args.length > 1 ? args[1] : "data");
        InetAddress bind = InetAddress.getByName(args.length > 2 ? args[2] : "127.0.0.1");
        try (MediaServer server = new MediaServer(bind, port, new Library(data), 8, 120_000)) {
            Thread shutdown = new Thread(server::close, "server-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            System.out.println("Media server listening on " + bind.getHostAddress() + ":" + server.port());
            System.out.println("8 concurrent sessions; save/load snapshots explicitly. Stop with Ctrl+C.");
            try { server.serve(); }
            finally { try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException ignored) { } }
        }
    }
}
