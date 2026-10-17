package server;

import common.ObjectCodec;
import common.Response;
import model.Media;
import model.MediaRules;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** One synchronized catalog; callers never receive references to stored objects. */
public final class Library {
    private final LinkedHashMap<String, Media> entries = new LinkedHashMap<>();
    private final Path dataDirectory;

    public Library(Path directory) throws IOException {
        Files.createDirectories(directory);
        dataDirectory = directory.toRealPath();
    }

    public synchronized void add(Media media) throws IOException {
        Media copy = MediaRules.copy(media);
        String key = MediaRules.key(copy.getTitle(), copy.getClass());
        if (entries.containsKey(key)) throw new IllegalArgumentException("A media item with this title and type already exists");
        if (entries.size() >= 1000) throw new IllegalArgumentException("Catalog limit is 1000 items");
        // Keep every accepted catalog small enough to return in a single framed response.
        ArrayList<Media> candidate = all();
        candidate.add(copy);
        checkCapacity(candidate);
        entries.put(key, copy);
    }

    public synchronized void update(Media media) throws IOException {
        Media copy = MediaRules.copy(media);
        String key = MediaRules.key(copy.getTitle(), copy.getClass());
        if (!entries.containsKey(key)) throw new IllegalArgumentException("Media item not found; title and type identify an update");
        LinkedHashMap<String, Media> candidate = new LinkedHashMap<>(entries);
        candidate.put(key, copy);
        checkCapacity(new ArrayList<>(candidate.values()));
        entries.put(key, copy);
    }

    public synchronized void delete(String title, Class<?> type) {
        if (entries.remove(MediaRules.key(title, type)) == null) throw new IllegalArgumentException("Media item not found");
    }

    public synchronized ArrayList<Media> all() {
        ArrayList<Media> result = new ArrayList<>();
        for (Media item : entries.values()) result.add(MediaRules.copy(item));
        return result;
    }

    public synchronized ArrayList<Media> search(String query, boolean genre) {
        String normalized = MediaRules.text(query, "Search", 200, true).toLowerCase(Locale.ROOT);
        ArrayList<Media> result = new ArrayList<>();
        for (Media media : entries.values()) {
            String value = (genre ? media.getGenre() : media.getTitle()).toLowerCase(Locale.ROOT);
            if (genre ? value.equals(normalized) : value.contains(normalized)) result.add(MediaRules.copy(media));
        }
        return result;
    }

    private static void checkCapacity(ArrayList<Media> items) throws IOException {
        // Reserve space for Response metadata, not just the serialized list.
        byte[] bytes = ObjectCodec.encode(new Response(true, "Catalog retrieved", items));
        if (bytes.length > ObjectCodec.MAX_BYTES - 8192) throw new IOException("Catalog exceeds snapshot/message capacity");
        ObjectCodec.decode(bytes); // A locally accepted catalog must also fit the graph limits on reload.
    }

    private Path snapshotPath(String name) throws IOException {
        if (name == null || name.isBlank()) name = "library.ser";
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}\\.ser")) {
            throw new IllegalArgumentException("Use a simple snapshot name such as library.ser, without directories");
        }
        Path path = dataDirectory.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Snapshot symlinks are not allowed");
        return path;
    }

    public synchronized void save(String name) throws IOException {
        Path target = snapshotPath(name);
        byte[] bytes = ObjectCodec.encode(all());
        Path temporary = Files.createTempFile(dataDirectory, ".snapshot-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized void load(String name) throws IOException {
        Path path = snapshotPath(name);
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(ObjectCodec.MAX_BYTES + 1);
        }
        Object decoded = ObjectCodec.decode(bytes);
        if (!(decoded instanceof ArrayList<?> list) || list.size() > 1000) throw new IOException("Invalid catalog snapshot");
        LinkedHashMap<String, Media> candidate = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Media media)) throw new IOException("Snapshot contains a non-media entry");
            Media copy = MediaRules.copy(media);
            String key = MediaRules.key(copy.getTitle(), copy.getClass());
            if (candidate.putIfAbsent(key, copy) != null) throw new IOException("Snapshot contains duplicate media");
        }
        checkCapacity(new ArrayList<>(candidate.values()));
        entries.clear();
        entries.putAll(candidate);
    }
}
