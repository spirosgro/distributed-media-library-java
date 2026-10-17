package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validate at the server boundary and detach mutable domain objects. */
public final class MediaRules {
    private MediaRules() { }

    public static String text(String value, String name, int maximum, boolean required) {
        if (value == null) value = "";
        String cleaned = value.strip();
        if ((required && cleaned.isEmpty()) || cleaned.length() > maximum
                || cleaned.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) {
            throw new IllegalArgumentException(name + " must contain " + (required ? "1" : "0") + "-" + maximum + " characters without control codes");
        }
        return cleaned;
    }

    private static int range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " must be " + minimum + "-" + maximum);
        return value;
    }

    public static String key(String title, Class<?> type) {
        if (type != Movie.class && type != Series.class) throw new IllegalArgumentException("Unsupported media type");
        return type.getSimpleName() + ":" + text(title, "Title", 200, true).toLowerCase(Locale.ROOT);
    }

    public static Media copy(Media media) {
        if (media == null) throw new IllegalArgumentException("Media is required");
        String title = text(media.getTitle(), "Title", 200, true);
        String description = text(media.getDescription(), "Description", 2000, false);
        String genre = text(media.getGenre(), "Genre", 80, true);
        int year = range(media.getFirstReleaseYear(), 1888, 2100, "Release year");
        if (media.getClass() == Movie.class) {
            Movie movie = (Movie) media;
            return new Movie(title, description, genre, year,
                    range(movie.getDurationMinutes(), 1, 1440, "Movie duration"),
                    text(movie.getDirector(), "Director", 200, true));
        }
        if (media.getClass() != Series.class) throw new IllegalArgumentException("Unsupported media type");
        Series series = (Series) media;
        int seasons = range(series.getNumberOfSeasons(), 1, 100, "Season count");
        ArrayList<Episode> episodes;
        try {
            episodes = series.getEpisodes();
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException("Episode list is required", exception);
        }
        if (episodes.size() > 100) throw new IllegalArgumentException("At most 100 episodes per series");
        ArrayList<Episode> copies = new ArrayList<>();
        Set<String> numbers = new HashSet<>();
        for (Object item : episodes) {
            if (!(item instanceof Episode episode)) throw new IllegalArgumentException("Episode list contains a non-episode value");
            int season = range(episode.getSeasonNumber(), 1, seasons, "Episode season");
            int number = range(episode.getEpisodeNumberInSeason(), 1, 1000, "Episode number");
            if (!numbers.add(season + ":" + number)) throw new IllegalArgumentException("Duplicate episode number in season");
            copies.add(new Episode(text(episode.getTitle(), "Episode title", 200, true), season, number,
                    range(episode.getDurationMinutes(), 1, 1440, "Episode duration")));
        }
        return new Series(title, description, genre, year, seasons, copies);
    }
}
