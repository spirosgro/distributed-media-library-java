package demo;

import client.ClientConnection;
import common.*;
import model.*;
import java.io.IOException;
import java.util.List;

/** Populate an empty demo server using the same network protocol as the GUI. */
public final class DemoClient {
    private DemoClient() { }
    public static void main(String[] args) throws Exception {
        if (args.length > 2) throw new IllegalArgumentException("Usage: DemoClient [port=5000] [host=127.0.0.1]");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String host = args.length > 1 ? args[1] : "127.0.0.1";
        try (ClientConnection client = new ClientConnection()) {
            client.connect(host, port);
            Response initial = send(client, new Request(RequestType.GET_ALL_MEDIA));
            if (!(initial.getData() instanceof List<?> list) || !list.isEmpty()) {
                throw new IOException("Demo requires an empty catalog. Use a separate demo server; existing entries were not changed.");
            }
            send(client, new Request(RequestType.ADD_MOVIE,
                    new Movie("The Quiet Horizon", "A cartographer maps an island after a storm.", "Drama", 2024, 95, "Alex Rivers"), null, null));
            send(client, new Request(RequestType.ADD_MOVIE,
                    new Movie("Paper Satellites", "Two friends build a radio station in their workshop.", "Comedy", 2023, 102, "Jordan Vale"), null, null));
            send(client, new Request(RequestType.ADD_SERIES,
                    new Series("Signals from Tomorrow", "A research crew follows an unexplained signal.", "Science Fiction", 2025, 2,
                            List.of(new Episode("First Light", 1, 1, 42), new Episode("The Signal", 1, 2, 44))), null, null));
            Response search = send(client, new Request(RequestType.SEARCH_BY_TITLE, null, "horizon", null));
            System.out.println("Title search matches: " + ((List<?>) search.getData()).size());
            send(client, new Request(RequestType.SAVE_LIBRARY, null, null, "demo.ser"));
            send(client, new Request(RequestType.DISCONNECT));
            System.out.println("Demo complete. Open the GUI and connect to inspect three authored entries. Snapshot: demo.ser.");
        }
    }

    private static Response send(ClientConnection client, Request request) throws IOException {
        Response response = client.sendRequest(request);
        if (!response.isSuccess()) throw new IOException(response.getMessage());
        System.out.println(response.getMessage());
        return response;
    }
}
