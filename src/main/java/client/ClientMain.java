package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public final class ClientMain {
    private ClientMain() { }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) { }
            ClientPanel panel = new ClientPanel();
            JFrame window = new JFrame("Distributed Media Library");
            window.setContentPane(panel);
            window.setSize(1200, 780);
            window.setMinimumSize(new Dimension(1080, 720));
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    if (panel.isBusy()) { Toolkit.getDefaultToolkit().beep(); return; }
                    panel.close(); window.dispose();
                }
            });
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}
