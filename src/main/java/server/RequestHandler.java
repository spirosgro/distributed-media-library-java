package server;

import common.Request;
import common.Response;
import model.Media;
import model.Movie;
import model.Series;
import java.io.IOException;

/** Application errors become responses; transport errors end the connection. */
public final class RequestHandler {
    private final Library library;
    public RequestHandler(Library library) { this.library = library; }

    private static <T> T payload(Request request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("Expected " + type.getSimpleName() + " payload");
        return type.cast(request.getPayload());
    }

    public Response handle(Request request) {
        if (request == null || request.getType() == null) return new Response(false, "Operation is required", null);
        try {
            Object result = null;
            String message;
            switch (request.getType()) {
                case ADD_MOVIE -> { library.add(payload(request, Movie.class)); message = "Movie added"; }
                case ADD_SERIES -> { library.add(payload(request, Series.class)); message = "Series added"; }
                case UPDATE_MOVIE -> { library.update(payload(request, Movie.class)); message = "Movie updated"; }
                case UPDATE_SERIES -> { library.update(payload(request, Series.class)); message = "Series updated"; }
                case DELETE_MEDIA -> {
                    Media media = payload(request, Media.class);
                    library.delete(media.getTitle(), media.getClass());
                    message = "Media deleted";
                }
                case SEARCH_BY_TITLE -> { result = library.search(request.getTextParameter(), false); message = "Title search completed"; }
                case SEARCH_BY_GENRE -> { result = library.search(request.getTextParameter(), true); message = "Genre search completed"; }
                case GET_ALL_MEDIA -> { result = library.all(); message = "Catalog retrieved"; }
                case SAVE_LIBRARY -> { library.save(request.getFileName()); message = "Snapshot saved"; }
                case LOAD_LIBRARY -> { library.load(request.getFileName()); message = "Snapshot loaded; catalog replaced"; }
                case DISCONNECT -> message = "Disconnected";
                default -> throw new IllegalArgumentException("Unsupported operation");
            }
            return new Response(true, message, result);
        } catch (IllegalArgumentException exception) {
            return new Response(false, exception.getMessage(), null);
        } catch (IOException exception) {
            // Do not send server filesystem paths to the client.
            return new Response(false, "Storage or capacity operation failed; check the snapshot name, file and server data directory", null);
        }
    }
}
