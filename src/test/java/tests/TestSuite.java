package tests;

import client.ClientConnection;
import client.ClientPanel;
import common.*;
import model.*;
import server.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Black-box network and domain regression checks; no external test dependencies. */
public final class TestSuite {
    private static int passed;
    @FunctionalInterface private interface Check { void run() throws Exception; }

    private TestSuite() { }
    private static void check(String name, Check body) throws Exception {
        body.run(); passed++; System.out.println("PASS " + name);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void reject(Check operation) throws Exception {
        try { operation.run(); }
        catch (IOException | IllegalArgumentException exception) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
    private static Movie movie(String title) { return new Movie(title, "Authored demo metadata", "Drama", 2024, 95, "Alex Rivers"); }
    private static Series series(String title) {
        return new Series(title, "Authored series metadata", "Science Fiction", 2025, 2,
                List.of(new Episode("First Light", 1, 1, 42), new Episode("The Signal", 1, 2, 44)));
    }
    private static Request request(RequestType type, Object payload) { return new Request(type, payload, null, null); }
    private static List<?> items(Response response) {
        require(response.isSuccess(), response.getMessage());
        return (List<?>) response.getData();
    }

    private static final class Fixture implements AutoCloseable {
        final Path directory = Files.createTempDirectory("media-library-test-");
        final Library library = new Library(directory);
        final MediaServer server;
        final Thread listener;
        final AtomicReference<Exception> failure = new AtomicReference<>();
        Fixture() throws IOException { this(8, 5000); }
        Fixture(int clients, int timeout) throws IOException {
            server = new MediaServer(InetAddress.getByName("127.0.0.1"), 0, library, clients, timeout);
            listener = new Thread(() -> {
                try { server.serve(); } catch (IOException exception) { failure.set(exception); }
            }, "test-acceptor");
            listener.start();
        }
        ClientConnection connect() throws IOException {
            ClientConnection client = new ClientConnection(); client.connect("127.0.0.1", server.port()); return client;
        }
        @Override public void close() throws IOException {
            server.close();
            try { listener.join(6000); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException(exception); }
            require(!listener.isAlive(), "Server listener did not stop");
            if (failure.get() != null) throw new IOException("Acceptor failed", failure.get());
            Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
            if (!directory.toRealPath().startsWith(tempRoot) || !directory.getFileName().toString().startsWith("media-library-test-")) {
                throw new IOException("Unexpected fixture cleanup target");
            }
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        check("CRUD, type-specific identity and search", () -> {
            try (Fixture f = new Fixture()) {
                f.library.add(movie("Shared Title")); f.library.add(series("Shared Title"));
                reject(() -> f.library.add(movie("  SHARED TITLE  ")));
                require(f.library.search("shared", false).size() == 2, "Partial title search");
                require(f.library.search("DRAMA", true).size() == 1, "Exact genre search");
                require(f.library.search("dra", true).isEmpty(), "Genre must be exact");
                Movie updated = movie("Shared Title"); updated.setDurationMinutes(120); f.library.update(updated);
                f.library.delete("shared title", Series.class);
                require(f.library.all().size() == 1 && ((Movie) f.library.all().get(0)).getDurationMinutes() == 120, "Wrong item updated/deleted");
                reject(() -> f.library.update(movie("Missing")));
            }
        });
        check("Server validation and deep defensive copies", () -> {
            try (Fixture f = new Fixture()) {
                Movie input = movie("Original"); f.library.add(input); input.setTitle("Mutated");
                f.library.all().get(0).setTitle("External mutation");
                require(f.library.search("original", false).size() == 1, "Stored reference escaped");
                Series inputSeries = series("Series"); f.library.add(inputSeries);
                inputSeries.getEpisodes().get(0).setTitle("Outside edit");
                Series output = (Series) f.library.search("Series", false).get(0);
                output.getEpisodes().get(0).setTitle("Returned edit");
                require(((Series) f.library.search("Series", false).get(0)).getEpisodes().get(0).getTitle().equals("First Light"), "Episode reference escaped");
                Movie bad = movie("Bad"); bad.setDurationMinutes(-1); reject(() -> f.library.add(bad));
                bad.setDurationMinutes(95); bad.setFirstReleaseYear(0); reject(() -> f.library.add(bad));
                reject(() -> f.library.add(movie("  ")));
                Series duplicate = series("Duplicate"); duplicate.setEpisodes(List.of(new Episode("A", 1, 1, 30), new Episode("B", 1, 1, 30)));
                reject(() -> f.library.add(duplicate));
                duplicate.setEpisodes(List.of(new Episode("A", 3, 1, 30))); reject(() -> f.library.add(duplicate));
            }
        });
        check("Locale-independent identity", () -> {
            Locale before = Locale.getDefault();
            try (Fixture f = new Fixture()) {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                f.library.add(movie("ISLAND"));
                require(f.library.search("island", false).size() == 1, "Locale-dependent search");
                reject(() -> f.library.add(movie("island")));
            } finally { Locale.setDefault(before); }
        });
        check("Deserialized episode element types are validated", () -> {
            try (Fixture f = new Fixture(); ClientConnection client = f.connect()) {
                Series corrupted = series("Corrupted");
                var field = Series.class.getDeclaredField("episodes");
                field.setAccessible(true);
                field.set(corrupted, new ArrayList<>(List.of("not an episode")));
                require(!client.sendRequest(request(RequestType.ADD_SERIES, corrupted)).isSuccess(), "Invalid generic element was accepted");
                require(items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).isEmpty(), "Bad element ended the session or changed state");
            }
        });
        check("Catalog capacity rejection preserves a reloadable snapshot", () -> {
            try (Fixture f = new Fixture()) {
                boolean rejected = false;
                for (int i = 0; i < 1000; i++) {
                    Movie item = movie("Capacity item " + i);
                    item.setDescription("x".repeat(2000));
                    try { f.library.add(item); }
                    catch (IOException exception) { rejected = true; break; }
                }
                require(rejected, "Expected byte or graph capacity limit");
                int size = f.library.all().size();
                f.library.save("capacity.ser"); f.library.load("capacity.ser");
                require(f.library.all().size() == size, "Last accepted catalog could not round trip");
                ObjectCodec.decode(ObjectCodec.encode(new Response(true, "Catalog retrieved", f.library.all())));
            }
        });
        check("Snapshot replacement, restart and failed-load rollback", () -> {
            try (Fixture f = new Fixture()) {
                f.library.add(movie("Saved")); f.library.save(null);
                f.library.add(series("Transient")); f.library.load(null);
                require(f.library.all().size() == 1, "Load should replace");
                Library restarted = new Library(f.directory);
                require(restarted.all().isEmpty(), "Startup must not silently load snapshots");
                restarted.load("library.ser"); require(restarted.all().size() == 1, "Restart round trip");
                Files.write(f.directory.resolve("broken.ser"), new byte[]{1, 2, 3});
                reject(() -> f.library.load("broken.ser"));
                ArrayList<Media> duplicates = new ArrayList<>(List.of(movie("X"), movie("x")));
                Files.write(f.directory.resolve("duplicate.ser"), ObjectCodec.encode(duplicates));
                reject(() -> f.library.load("duplicate.ser"));
                Movie bad = movie("Invalid"); bad.setDurationMinutes(0);
                Files.write(f.directory.resolve("invalid.ser"), ObjectCodec.encode(new ArrayList<>(List.of(bad))));
                reject(() -> f.library.load("invalid.ser"));
                require(f.library.all().get(0).getTitle().equals("Saved"), "Failed load changed live catalog");
            }
        });
        check("Snapshot path traversal rejected", () -> {
            try (Fixture f = new Fixture()) {
                for (String path : List.of("../escape.ser", "folder/file.ser", "folder\\file.ser", "C:\\escape.ser", "/tmp/escape.ser", "bad.txt")) {
                    reject(() -> f.library.save(path)); reject(() -> f.library.load(path));
                }
            }
        });
        check("Object allowlist, graph limits and byte limits", () -> {
            reject(() -> ObjectCodec.decode(ObjectCodec.encode(new Date())));
            reject(() -> ObjectCodec.decode(ObjectCodec.encode(new byte[12])));
            ArrayList<Object> list = new ArrayList<>(); ArrayList<Object> cursor = list;
            for (int i = 0; i < 30; i++) { ArrayList<Object> child = new ArrayList<>(); cursor.add(child); cursor = child; }
            reject(() -> ObjectCodec.decode(ObjectCodec.encode(list)));
            reject(() -> ObjectCodec.encode("x".repeat(ObjectCodec.MAX_BYTES + 1)));
            reject(() -> ObjectCodec.decode(new byte[ObjectCodec.MAX_BYTES + 1]));
            reject(() -> ObjectCodec.decode(ObjectCodec.encode(new ArrayList<>(java.util.Collections.nCopies(10001, "x")))));
        });
        check("Every protocol operation over actual TCP", () -> {
            try (Fixture f = new Fixture(); ClientConnection client = f.connect()) {
                require(client.sendRequest(request(RequestType.ADD_MOVIE, movie("Network Movie"))).isSuccess(), "Add movie");
                require(client.sendRequest(request(RequestType.ADD_SERIES, series("Network Series"))).isSuccess(), "Add series");
                Movie update = movie("Network Movie"); update.setDirector("Jordan Vale");
                require(client.sendRequest(request(RequestType.UPDATE_MOVIE, update)).isSuccess(), "Update movie");
                Series s = series("Network Series"); s.setEpisodes(List.of(new Episode("New Episode", 2, 1, 50)));
                require(client.sendRequest(request(RequestType.UPDATE_SERIES, s)).isSuccess(), "Update series");
                require(items(client.sendRequest(new Request(RequestType.SEARCH_BY_TITLE, null, "network", null))).size() == 2, "Search title");
                require(items(client.sendRequest(new Request(RequestType.SEARCH_BY_GENRE, null, "Drama", null))).size() == 1, "Search genre");
                require(client.sendRequest(new Request(RequestType.SAVE_LIBRARY)).isSuccess(), "Save");
                require(client.sendRequest(request(RequestType.DELETE_MEDIA, update)).isSuccess(), "Delete");
                require(items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).size() == 1, "List after delete");
                require(client.sendRequest(new Request(RequestType.LOAD_LIBRARY)).isSuccess(), "Load");
                require(items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).size() == 2, "List after load");
                require(client.sendRequest(new Request(RequestType.DISCONNECT)).isSuccess(), "Disconnect acknowledgement");
                reject(() -> client.sendRequest(new Request(RequestType.GET_ALL_MEDIA)));
                require(!client.isConnected(), "Failed socket remained marked connected");
            }
        });
        check("Invalid application requests preserve the connection", () -> {
            try (Fixture f = new Fixture(); ClientConnection client = f.connect()) {
                require(!client.sendRequest(new Request()).isSuccess(), "Null operation accepted");
                require(!client.sendRequest(request(RequestType.ADD_MOVIE, "wrong type")).isSuccess(), "Wrong payload accepted");
                require(!client.sendRequest(new Request(RequestType.SAVE_LIBRARY, null, null, "../bad.ser")).isSuccess(), "Unsafe filename accepted");
                require(items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).isEmpty(), "Connection poisoned");
            }
        });
        check("Frames do not reuse stale object references", () -> {
            try (Fixture f = new Fixture(); ClientConnection client = f.connect()) {
                Movie value = movie("Mutable"); Request envelope = request(RequestType.ADD_MOVIE, value);
                require(client.sendRequest(envelope).isSuccess(), "Initial add");
                envelope.setType(RequestType.UPDATE_MOVIE); value.setDurationMinutes(130);
                require(client.sendRequest(envelope).isSuccess(), "Updated request was cached");
                Movie fetched = (Movie) items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).get(0);
                require(fetched.getDurationMinutes() == 130, "Payload was cached across frames");
            }
        });
        check("Concurrent duplicate insertion is atomic", () -> {
            try (Fixture f = new Fixture()) {
                ExecutorService pool = Executors.newFixedThreadPool(4);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    List<Future<Boolean>> futures = new ArrayList<>();
                    for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> {
                        try (ClientConnection client = f.connect()) {
                            start.await(); return client.sendRequest(request(RequestType.ADD_MOVIE, movie("One winner"))).isSuccess();
                        }
                    }));
                    start.countDown();
                    int winners = 0;
                    for (Future<Boolean> future : futures) if (future.get(10, TimeUnit.SECONDS)) winners++;
                    require(winners == 1 && f.library.all().size() == 1, "Duplicate race was not atomic");
                } finally { pool.shutdownNow(); }
            }
        });
        check("Malformed length and filtered types isolate the offending session", () -> {
            try (Fixture f = new Fixture()) {
                for (int length : new int[]{-1, 0, ObjectCodec.MAX_BYTES + 1}) {
                    try (Socket socket = new Socket("127.0.0.1", f.server.port())) {
                        socket.setSoTimeout(2000);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeInt(FramedChannel.MAGIC); out.writeInt(length); out.flush();
                        require(socket.getInputStream().read() == -1, "Bad length not closed");
                    }
                }
                try (Socket socket = new Socket("127.0.0.1", f.server.port())) {
                    socket.setSoTimeout(2000);
                    new FramedChannel(socket).send(new Date());
                    require(socket.getInputStream().read() == -1, "Disallowed class not closed");
                }
                try (ClientConnection client = f.connect()) {
                    require(items(client.sendRequest(new Request(RequestType.GET_ALL_MEDIA))).isEmpty(), "Server stopped after malformed input");
                }
            }
        });
        check("Idle clients expire and excess clients are rejected", () -> {
            try (Fixture f = new Fixture(1, 1500); ClientConnection first = f.connect()) {
                first.sendRequest(new Request(RequestType.GET_ALL_MEDIA));
                try (Socket excess = new Socket("127.0.0.1", f.server.port())) {
                    excess.setSoTimeout(2000);
                    require(excess.getInputStream().read() == -1, "Excess connection was queued indefinitely");
                }
                Thread.sleep(1800);
                reject(() -> first.sendRequest(new Request(RequestType.GET_ALL_MEDIA)));
                try (ClientConnection replacement = f.connect()) {
                    require(replacement.sendRequest(new Request(RequestType.GET_ALL_MEDIA)).isSuccess(), "Idle session capacity not released");
                }
            }
        });
        check("Swing connects and searches asynchronously over TCP; render preview", TestSuite::swing);
        check("Demo client seeds and persists once without overwriting a catalog", () -> {
            try (Fixture f = new Fixture()) {
                demo.DemoClient.main(new String[]{String.valueOf(f.server.port())});
                require(f.library.all().size() == 3, "Demo did not create three entries");
                require(Files.isRegularFile(f.directory.resolve("demo.ser")), "Demo snapshot missing");
                reject(() -> demo.DemoClient.main(new String[]{String.valueOf(f.server.port())}));
                require(f.library.all().size() == 3, "Repeated demo changed the catalog");
            }
        });
        System.out.println("ALL " + passed + " SCENARIOS PASSED on " + System.getProperty("java.version") + " / " + System.getProperty("os.name"));
    }

    private static Component named(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component match = named(container, name); if (match != null) return match;
            }
        }
        return null;
    }
    private static void awaitIdle(ClientPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AtomicInteger busy = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> busy.set(panel.isBusy() ? 1 : 0));
            if (busy.get() == 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Swing operation did not finish");
    }
    private static void layout(Container component) {
        component.doLayout();
        for (Component child : component.getComponents()) if (child instanceof Container container) layout(container);
    }
    private static void swing() throws Exception {
        try (Fixture f = new Fixture()) {
            f.library.add(movie("The Quiet Horizon")); f.library.add(series("Signals from Tomorrow"));
            AtomicReference<ClientPanel> reference = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ClientPanel panel = new ClientPanel(); reference.set(panel);
                ((JTextField) named(panel, "port")).setText(String.valueOf(f.server.port()));
                ((JButton) named(panel, "Connect")).doClick();
                require(panel.isBusy(), "Network operation did not use background worker");
            });
            ClientPanel panel = reference.get();
            try {
                awaitIdle(panel);
                SwingUtilities.invokeAndWait(() -> {
                    JTable table = (JTable) named(panel, "catalog");
                    require(table.getRowCount() == 2, "GUI failed to retrieve catalog");
                    table.setRowSelectionInterval(1, 1);
                    panel.setSize(1200, 780); layout(panel);
                    BufferedImage preview = new BufferedImage(1200, 780, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = preview.createGraphics(); panel.printAll(graphics); graphics.dispose();
                    try { Files.createDirectories(Path.of("build")); ImageIO.write(preview, "png", Path.of("build/ui-preview.png").toFile()); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                    ((JTextField) named(panel, "query")).setText("horizon");
                    ((JButton) named(panel, "By title")).doClick();
                });
                awaitIdle(panel);
                SwingUtilities.invokeAndWait(() -> require(((JTable) named(panel, "catalog")).getRowCount() == 1, "GUI search did not filter"));
            } finally { SwingUtilities.invokeAndWait(panel::close); }
        }
    }
}
