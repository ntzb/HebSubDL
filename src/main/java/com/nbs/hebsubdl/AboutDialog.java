package com.nbs.hebsubdl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public class AboutDialog {
    private static final String PROJECT_URL = "https://github.com/ntzb/HebSubDL";

    public static void show(JFrame owner) {
        SwingUtilities.invokeLater(() -> buildAndShow(owner));
    }

    private static void buildAndShow(JFrame owner) {
        JDialog dialog = new JDialog(owner, "About HebSubDL", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Hebrew Subtitle Downloader");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 2f));
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        content.add(new JLabel("Version: " + AppVersion.get()));
        content.add(Box.createVerticalStrut(6));
        content.add(createLink());
        content.add(Box.createVerticalStrut(12));

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.add(closeButton);
        content.add(buttonRow);

        for (Component component : content.getComponents())
            ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(closeButton);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private static JLabel createLink() {
        JLabel link = new JLabel("<html><a href=\"" + PROJECT_URL + "\">" + PROJECT_URL + "</a></html>");
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText("Open in browser");
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openInBrowser();
            }
        });
        return link;
    }

    private static void openInBrowser() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Logger.logger.warning("cannot open browser - Desktop browse action not supported.");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(PROJECT_URL));
        } catch (Exception e) {
            Logger.logException(e, "opening the project URL in a browser.");
        }
    }
}
