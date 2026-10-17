package common;

import model.Episode;
import model.Media;
import model.Movie;
import model.Series;
import java.io.*;
import java.util.ArrayList;
import java.util.Set;

/** Bounded, allowlisted object encoding shared by messages and local snapshots. */
public final class ObjectCodec {
    public static final int MAX_BYTES = 1_048_576;
    private static final Set<Class<?>> TYPES = Set.of(Request.class, RequestType.class,
            Response.class, Media.class, Movie.class, Series.class, Episode.class,
            ArrayList.class, String.class, Enum.class);

    private ObjectCodec() { }

    public static byte[] encode(Object value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputStream limited = new OutputStream() {
            @Override public void write(int value) throws IOException {
                if (buffer.size() >= MAX_BYTES) throw new IOException("Object exceeds 1 MiB limit");
                buffer.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                if (length > MAX_BYTES - buffer.size()) throw new IOException("Object exceeds 1 MiB limit");
                buffer.write(bytes, offset, length);
            }
        };
        try (ObjectOutputStream stream = new ObjectOutputStream(limited)) {
            stream.writeObject(value);
        }
        return buffer.toByteArray();
    }

    public static Object decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid object size");
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            stream.setObjectInputFilter(info -> {
                if (info.depth() > 20 || info.references() > 20_000 || info.arrayLength() > 10_000
                        || info.streamBytes() > MAX_BYTES) return ObjectInputFilter.Status.REJECTED;
                Class<?> type = info.serialClass();
                if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                // ArrayList requests an Object[] allocation; each actual element is filtered separately.
                if (type == Object[].class || TYPES.contains(type)) return ObjectInputFilter.Status.ALLOWED;
                return ObjectInputFilter.Status.REJECTED;
            });
            Object result = stream.readObject();
            if (stream.read() != -1) throw new IOException("Trailing object data");
            return result;
        } catch (ClassNotFoundException exception) {
            throw new IOException("Unsupported object class", exception);
        }
    }
}
