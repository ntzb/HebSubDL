package com.nbs.hebsubdl;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Edits watch.directories: a row per folder, each with its own Browse button.
// Built in code rather than in the GUI designer, since the rows come and go.
public class WatchDirsDialog extends JDialog {
    private final JPanel rowsPanel = new JPanel();
    private final List<JTextField> fields = new ArrayList<>();
    private List<String> result;

    private WatchDirsDialog(Window owner, List<String> dirs) {
        super(owner, "Watched folders", ModalityType.APPLICATION_MODAL);
        rowsPanel.setLayout(new GridBagLayout());
        for (String dir : dirs)
            addRow(dir);
        if (dirs.isEmpty())
            addRow("");

        JScrollPane scrollPane = new JScrollPane(rowsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JButton addButton = new JButton("Add folder");
        addButton.addActionListener(e -> {
            addRow("");
            refreshRows();
        });
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> onOK());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okButton);
        buttons.add(cancelButton);
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        addPanel.add(addButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(addPanel, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        JLabel hint = new JLabel("New video files in these folders, and in their subfolders, are searched automatically.");
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(hint, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        refreshRows();
    }

    // returns the edited list, or null if the dialog was cancelled
    public static List<String> edit(Window owner, List<String> dirs) {
        WatchDirsDialog dialog = new WatchDirsDialog(owner, dirs);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void addRow(String dir) {
        JTextField field = new JTextField(dir, 40);
        fields.add(field);
    }

    private void refreshRows() {
        rowsPanel.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 4);
        for (int row = 0; row < fields.size(); row++) {
            JTextField field = fields.get(row);
            c.gridy = row;
            c.gridx = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            rowsPanel.add(field, c);

            JButton browse = new JButton("Browse...");
            browse.addActionListener(e -> browse(field));
            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            rowsPanel.add(browse, c);

            JButton remove = new JButton("Remove");
            remove.addActionListener(e -> {
                fields.remove(field);
                refreshRows();
            });
            c.gridx = 2;
            rowsPanel.add(remove, c);
        }
        // keeps the rows at the top when there's spare height
        c.gridy = fields.size();
        c.gridx = 0;
        c.weighty = 1;
        rowsPanel.add(Box.createGlue(), c);
        c.weighty = 0;

        rowsPanel.revalidate();
        rowsPanel.repaint();
        pack();
        // grow with the rows up to a sensible height, then scroll
        Dimension size = getSize();
        int maxHeight = getGraphicsConfiguration().getBounds().height * 2 / 3;
        setSize(Math.max(size.width, 560), Math.min(size.height, maxHeight));
        if (!isShowing())
            setLocationRelativeTo(getOwner());
    }

    private void browse(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = field.getText().trim();
        if (!current.isEmpty() && new File(current).isDirectory())
            chooser.setCurrentDirectory(new File(current));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            field.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    private void onOK() {
        Set<String> dirs = new LinkedHashSet<>();
        for (JTextField field : fields) {
            String dir = field.getText().trim();
            if (dir.isEmpty())
                continue;
            // the config stores them comma separated
            if (dir.contains(",")) {
                showError("Folder names with a comma can't be watched:\n" + dir);
                return;
            }
            if (!new File(dir).isDirectory()) {
                showError("This folder doesn't exist:\n" + dir);
                return;
            }
            dirs.add(dir);
        }
        result = new ArrayList<>(dirs);
        dispose();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Watched folders", JOptionPane.ERROR_MESSAGE);
    }

    static List<String> parse(String watchDirectories) {
        List<String> dirs = new ArrayList<>();
        if (watchDirectories == null)
            return dirs;
        for (String dir : watchDirectories.split(",")) {
            if (!dir.trim().isEmpty())
                dirs.add(dir.trim());
        }
        return dirs;
    }
}
