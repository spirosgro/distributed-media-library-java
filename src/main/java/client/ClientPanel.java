package client;

import common.*;
import model.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Swing state stays on the EDT; one background worker owns each network operation. */
@SuppressWarnings("serial") // Swing panels are never serialized by this application.
public final class ClientPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    private final ClientConnection connection = new ClientConnection();
    private final JTextField host = field("127.0.0.1", 12, "host");
    private final JTextField port = field("5000", 5, "port");
    private final JTextField query = field("", 20, "query");
    private final JTextField snapshot = field("library.ser", 14, "snapshot");
    private final JLabel status = new JLabel("Disconnected");
    private final JTextArea details = new JTextArea();
    private final JTextArea messages = new JTextArea("Start the server, then connect. Changes are saved only when you select Save.\n");
    private final List<JButton> actions = new ArrayList<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Type", "Title", "Genre", "Year"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private List<Media> displayed = List.of();
    private boolean busy;

    private record Outcome(String message, List<Media> items) { }

    public ClientPanel() {
        super(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("MEDIA LIBRARY");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        top.add(title);
        top.add(new JLabel("Movies, series and episodes - one shared server catalog"));
        JPanel connectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        connectionRow.add(new JLabel("Host")); connectionRow.add(host);
        connectionRow.add(new JLabel("Port")); connectionRow.add(port);
        connectionRow.add(button("Connect", this::connect));
        connectionRow.add(button("Disconnect", () -> run(() -> {
            connection.close(); return new Outcome("Disconnected; unsaved catalog remains on the server", null);
        })));
        status.setName("status");
        connectionRow.add(status);
        top.add(connectionRow);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchRow.add(new JLabel("Search")); searchRow.add(query);
        searchRow.add(button("By title", () -> request(new Request(RequestType.SEARCH_BY_TITLE, null, query.getText(), null), false)));
        searchRow.add(button("By genre", () -> request(new Request(RequestType.SEARCH_BY_GENRE, null, query.getText(), null), false)));
        searchRow.add(button("Show all", () -> request(new Request(RequestType.GET_ALL_MEDIA), false)));
        top.add(searchRow);
        JPanel editRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        editRow.add(button("Add movie", () -> edit(false, null)));
        editRow.add(button("Add series", () -> edit(true, null)));
        editRow.add(button("Edit selected", () -> {
            Media selected = selected();
            if (selected != null) edit(selected instanceof Series, selected);
        }));
        editRow.add(button("Delete selected", this::delete));
        top.add(editRow);
        add(top, BorderLayout.NORTH);
        table.setName("catalog");
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            int selected = table.getSelectedRow();
            if (selected >= 0 && !event.getValueIsAdjusting()) showDetails(displayed.get(table.convertRowIndexToModel(selected)));
        });
        details.setEditable(false);
        details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane left = new JScrollPane(table);
        left.setBorder(BorderFactory.createTitledBorder("Catalog"));
        JScrollPane right = new JScrollPane(details);
        right.setBorder(BorderFactory.createTitledBorder("Selected item"));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.65); split.setDividerLocation(680);
        add(split, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JPanel persistence = new JPanel(new FlowLayout(FlowLayout.LEFT));
        persistence.add(new JLabel("Server snapshot")); persistence.add(snapshot);
        persistence.add(button("Save", () -> request(new Request(RequestType.SAVE_LIBRARY, null, null, snapshot.getText()), false)));
        persistence.add(button("Load", () -> {
            if (JOptionPane.showConfirmDialog(this, "Replace the shared catalog for every connected client?", "Load snapshot",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                request(new Request(RequestType.LOAD_LIBRARY, null, null, snapshot.getText()), true);
            }
        }));
        bottom.add(persistence, BorderLayout.NORTH);
        messages.setEditable(false); messages.setRows(4); messages.setLineWrap(true); messages.setWrapStyleWord(true);
        messages.setName("messages");
        bottom.add(new JScrollPane(messages), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private static JTextField field(String text, int columns, String name) {
        JTextField field = new JTextField(text, columns); field.setName(name); return field;
    }

    private JButton button(String title, Runnable action) {
        JButton button = new JButton(title);
        button.setName(title);
        button.addActionListener(event -> action.run());
        actions.add(button);
        return button;
    }

    private void connect() {
        String hostname = host.getText().strip();
        int number;
        try { number = Integer.parseInt(port.getText().strip()); }
        catch (NumberFormatException exception) { messages.append("Port must be a number\n"); return; }
        if (number < 1 || number > 65535 || hostname.isEmpty()) { messages.append("Enter a host and port 1-65535\n"); return; }
        run(() -> {
            connection.connect(hostname, number);
            return new Outcome("Connected to " + hostname + ":" + number, mediaList(checked(new Request(RequestType.GET_ALL_MEDIA))));
        });
    }

    private Response checked(Request request) throws IOException {
        Response response = connection.sendRequest(request);
        if (!response.isSuccess()) throw new IOException(response.getMessage());
        return response;
    }

    private static List<Media> mediaList(Response response) throws IOException {
        if (!(response.getData() instanceof List<?> list)) throw new IOException("Invalid catalog response");
        ArrayList<Media> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Media media)) throw new IOException("Invalid catalog item");
            result.add(MediaRules.copy(media));
        }
        return result;
    }

    private void request(Request request, boolean refresh) {
        run(() -> {
            Response response = checked(request);
            List<Media> items = refresh ? mediaList(checked(new Request(RequestType.GET_ALL_MEDIA)))
                    : response.getData() instanceof List<?> ? mediaList(response) : null;
            return new Outcome(response.getMessage(), items);
        });
    }

    private void run(Callable<Outcome> operation) {
        if (busy) return;
        busy = true;
        actions.forEach(button -> button.setEnabled(false));
        status.setText("Working...");
        new SwingWorker<Outcome, Void>() {
            @Override protected Outcome doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                try {
                    Outcome outcome = get();
                    messages.append(outcome.message() + "\n");
                    if (outcome.items() != null) {
                        table.clearSelection();
                        displayed = outcome.items();
                        tableModel.setRowCount(0);
                        for (Media media : displayed) tableModel.addRow(new Object[]{media.getClass().getSimpleName(), media.getTitle(), media.getGenre(), media.getFirstReleaseYear()});
                        details.setText(displayed.size() + " item(s). Select a row to inspect metadata.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    messages.append("Operation failed: " + exception.getCause().getMessage() + "\n");
                } finally {
                    busy = false;
                    actions.forEach(button -> button.setEnabled(true));
                    status.setText(connection.isConnected() ? "Connected" : "Disconnected");
                    messages.setCaretPosition(messages.getDocument().getLength());
                }
            }
        }.execute();
    }

    private Media selected() {
        int row = table.getSelectedRow();
        if (row < 0) { messages.append("Select an item first\n"); return null; }
        return displayed.get(table.convertRowIndexToModel(row));
    }

    private void edit(boolean series, Media original) {
        Media media = MediaEditor.edit(this, series, original);
        if (media == null) return;
        RequestType type = original == null ? series ? RequestType.ADD_SERIES : RequestType.ADD_MOVIE
                : series ? RequestType.UPDATE_SERIES : RequestType.UPDATE_MOVIE;
        request(new Request(type, media, null, null), true);
    }

    private void delete() {
        Media selected = selected();
        if (selected != null && JOptionPane.showConfirmDialog(this, "Delete " + selected.getClass().getSimpleName()
                + " '" + selected.getTitle() + "'?", "Delete media", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            request(new Request(RequestType.DELETE_MEDIA, selected, null, null), true);
        }
    }

    private void showDetails(Media media) {
        StringBuilder text = new StringBuilder(media.getTitle()).append("\n\n").append(media.getGenre())
                .append(" | ").append(media.getFirstReleaseYear()).append("\n\n").append(media.getDescription()).append("\n\n");
        if (media instanceof Movie movie) text.append(movie.getDurationMinutes()).append(" minutes\nDirector: ").append(movie.getDirector());
        if (media instanceof Series series) {
            text.append(series.getNumberOfSeasons()).append(" season(s)\n");
            for (Episode episode : series.getEpisodes()) text.append("S").append(episode.getSeasonNumber()).append(" E")
                    .append(episode.getEpisodeNumberInSeason()).append(" - ").append(episode.getTitle()).append(" (")
                    .append(episode.getDurationMinutes()).append(" min)\n");
        }
        details.setText(text.toString()); details.setCaretPosition(0);
    }

    public boolean isBusy() { return busy; }
    @Override public void close() { connection.close(); }
}
