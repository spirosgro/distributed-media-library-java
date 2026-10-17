package client;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/** Modal form for movie metadata or a series with a complete episode list. */
final class MediaEditor {
    private MediaEditor() { }

    static Media edit(Component parent, boolean series, Media original) {
        JPanel form = new JPanel(new GridBagLayout());
        JTextField title = new JTextField(original == null ? "" : original.getTitle(), 26);
        title.setEditable(original == null);
        JTextArea description = new JTextArea(original == null ? "" : original.getDescription(), 3, 26);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JTextField genre = new JTextField(original == null ? "" : original.getGenre());
        JTextField year = new JTextField(original == null ? "2026" : String.valueOf(original.getFirstReleaseYear()));
        JTextField durationOrSeasons = new JTextField(original instanceof Movie m ? String.valueOf(m.getDurationMinutes())
                : original instanceof Series s ? String.valueOf(s.getNumberOfSeasons()) : series ? "1" : "90");
        JTextField director = new JTextField(original instanceof Movie m ? m.getDirector() : "");
        JTextArea episodes = new JTextArea(8, 36);
        if (original instanceof Series value) {
            for (Episode episode : value.getEpisodes()) {
                episodes.append(episode.getSeasonNumber() + " | " + episode.getEpisodeNumberInSeason() + " | "
                        + episode.getDurationMinutes() + " | " + episode.getTitle() + "\n");
            }
        }
        row(form, 0, "Title (fixed when editing)", title);
        row(form, 1, "Description", new JScrollPane(description));
        row(form, 2, "Genre", genre);
        row(form, 3, "Release year", year);
        row(form, 4, series ? "Number of seasons" : "Duration in minutes", durationOrSeasons);
        if (series) {
            row(form, 5, "Episodes: season | number | minutes | title", new JScrollPane(episodes));
            row(form, 6, "One episode per line; blank list is allowed", new JLabel("Example: 1 | 1 | 42 | First Light"));
        } else row(form, 5, "Director", director);
        while (JOptionPane.showConfirmDialog(parent, form, (original == null ? "Add " : "Edit ") + (series ? "series" : "movie"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                int release = Integer.parseInt(year.getText().strip());
                int value = Integer.parseInt(durationOrSeasons.getText().strip());
                Media result;
                if (series) {
                    ArrayList<Episode> items = new ArrayList<>();
                    for (String line : episodes.getText().split("\\R")) {
                        if (line.isBlank()) continue;
                        String[] parts = line.split("\\|", 4);
                        if (parts.length != 4) throw new IllegalArgumentException("Episode format: season | number | minutes | title");
                        items.add(new Episode(parts[3].strip(), Integer.parseInt(parts[0].strip()),
                                Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip())));
                    }
                    result = new Series(title.getText(), description.getText(), genre.getText(), release, value, items);
                } else result = new Movie(title.getText(), description.getText(), genre.getText(), release, value, director.getText());
                return MediaRules.copy(result);
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(parent, "Check the form: " + exception.getMessage(), "Invalid media", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    private static void row(JPanel panel, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }
}
