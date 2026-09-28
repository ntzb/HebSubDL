package com.nbs.hebsubdl;

import com.nbs.hebsubdl.SubProviders.FindSubs;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.List;

public class SettingsDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField ktuvitUsernameField;
    private JPasswordField ktuvitPasswordField;
    private JTextField LanguageSuffixField;
    private JTextField openSubtitlesUsernameField;
    private JLabel ktuvitPasswordLabel;
    private JLabel ktuvitUsernameLabel;
    private JLabel languageSuffixLabel;
    private JLabel openSubtitlesUsernameLabel;
    private JLabel openSubtitlesPasswordLabel;
    private JPasswordField openSubtitlesPasswordField;
    private JLabel openSubtitlesApiKeyLabel;
    private JTextField openSubtitlesApiKeyField;
    private JLabel openSubtitlesUserAgentLabel;
    private JTextField openSubtitlesUserAgentField;
    private JLabel logLevelLabel;
    private JComboBox<String> logLevelComboBox;
    private JLabel watchIgnoreKeywordsLabel;
    private JTextField watchIgnoreKeywordsField;
    private JLabel watchDirectoriesLabel;
    private JButton watchDirectoriesButton;
    private List<String> watchDirs;
    private String loadedLogLevel;

    static final String[] LOG_LEVELS = { "severe", "warning", "info", "fine", "finer", "finest" };


    public SettingsDialog() {
        Logger.logger.finer("initializing settings dialog");
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        readProperties();

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        watchDirectoriesButton.addActionListener(e -> {
            List<String> edited = WatchDirsDialog.edit(this, watchDirs);
            if (edited != null) {
                watchDirs = edited;
                showWatchDirs();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        String ktuvitUsername = ktuvitUsernameField.getText().trim();
        String ktuvitPassword = new String(ktuvitPasswordField.getPassword());
        String langSuffix = LanguageSuffixField.getText().trim();
        String openSubtitlesUsername = openSubtitlesUsernameField.getText().trim();
        String openSubtitlesPassword = new String(openSubtitlesPasswordField.getPassword());
        String openSubtitlesApiKey = new String(openSubtitlesApiKeyField.getText().trim());
        String openSubtitlesUserAgent = new String(openSubtitlesUserAgentField.getText().trim());
        String logLevel = (String) logLevelComboBox.getSelectedItem();
        String watchIgnoreKeywords = watchIgnoreKeywordsField.getText().trim();
        String watchDirectories = String.join(",", watchDirs);
        boolean watchDirsChanged = !WatchDirsDialog.parse(PropertiesClass.getWatchDirectories()).equals(watchDirs);

        boolean ktuvitChanged = !ktuvitUsername.equals(PropertiesClass.getKtuvitUsername())
                || !ktuvitPassword.equals(PropertiesClass.getKtuvitPassword());

        HashMap<String, String> properties = new HashMap<>();
        properties.put("ktuvitUsername", ktuvitUsername);
        properties.put("ktuvitPassword", ktuvitPassword);
        properties.put("langSuffix", (langSuffix.isEmpty() || langSuffix.startsWith(".")) ? langSuffix : "." + langSuffix);
        properties.put("openSubtitlesUsername", openSubtitlesUsername);
        properties.put("openSubtitlesPassword", openSubtitlesPassword);
        properties.put("openSubtitlesApiKey", openSubtitlesApiKey);
        properties.put("openSubtitlesUserAgent", openSubtitlesUserAgent);
        // only when changed, so a hand-written "DEBUG" isn't rewritten as "finest"
        if (!logLevel.equals(loadedLogLevel))
            properties.put("logLevel", logLevel);
        properties.put("watchIgnoreKeywords", watchIgnoreKeywords);
        if (watchDirsChanged)
            properties.put("watchDirectories", watchDirectories);
        PropertiesClass.writeProperties(properties);
        if (watchDirsChanged)
            MainGUI.restartDirWatcher();

        if (ktuvitChanged) {
            DbAccess dbAccess = new DbAccess();
            dbAccess.clearLogin();
            dbAccess.close();
        }
        FindSubs.reinitProviders();

        dispose();
    }

    private void onCancel() {
        dispose();
    }

    public void showDiag() {
        readProperties();
        SettingsDialog dialog = new SettingsDialog();
        dialog.pack();
        dialog.setVisible(true);
    }

    private void readProperties() {
        Logger.logger.finer("reading properties");
        PropertiesClass.readProperties();
        ktuvitUsernameField.setText(PropertiesClass.getKtuvitUsername());
        ktuvitPasswordField.setText(PropertiesClass.getKtuvitPassword());
        LanguageSuffixField.setText(PropertiesClass.getLangSuffix());
        openSubtitlesUsernameField.setText(PropertiesClass.getOpenSubtitlesUsername());
        openSubtitlesPasswordField.setText(PropertiesClass.getOpenSubtitlesPassword());
        openSubtitlesApiKeyField.setText(PropertiesClass.getOpenSubtitlesApiKey());
        openSubtitlesUserAgentField.setText(PropertiesClass.getOpenSubtitlesUserAgent());
        loadedLogLevel = normalizeLogLevel(PropertiesClass.getLogLevel());
        logLevelComboBox.setSelectedItem(loadedLogLevel);
        watchIgnoreKeywordsField.setText(PropertiesClass.getWatchIgnoreKeywords());
        watchDirs = WatchDirsDialog.parse(PropertiesClass.getWatchDirectories());
        showWatchDirs();
    }

    private void showWatchDirs() {
        watchDirectoriesButton.setText(watchDirs.isEmpty() ? "none - Edit..."
                : watchDirs.size() == 1 ? "1 folder - Edit..." : watchDirs.size() + " folders - Edit...");
        watchDirectoriesButton.setToolTipText(watchDirs.isEmpty() ? null
                : "<html>" + String.join("<br>", watchDirs) + "</html>");
    }

    // the config also takes "error" and "debug", which are the same levels
    static String normalizeLogLevel(String level) {
        if (level == null || level.isBlank())
            return "info";
        String lower = level.trim().toLowerCase();
        if (lower.equals("error"))
            return "severe";
        if (lower.equals("debug"))
            return "finest";
        for (String known : LOG_LEVELS) {
            if (known.equals(lower))
                return lower;
        }
        return "info";
    }

    // NOTE: if adding GUI elements in intellij idea does not add code in this file,
    // go to (in intellij idea) Code -> Generate Module-info descriptors

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        buttonOK.setToolTipText("save changes");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        buttonCancel.setToolTipText("discard changes");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(11, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        ktuvitUsernameLabel = new JLabel();
        ktuvitUsernameLabel.setText("Ktuvit username:");
        panel3.add(ktuvitUsernameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        ktuvitUsernameField = new JTextField();
        panel3.add(ktuvitUsernameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel3.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        ktuvitPasswordLabel = new JLabel();
        ktuvitPasswordLabel.setText("Ktuvit password:");
        panel3.add(ktuvitPasswordLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ktuvitPasswordField = new JPasswordField();
        panel3.add(ktuvitPasswordField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        LanguageSuffixField = new JTextField();
        LanguageSuffixField.setToolTipText("can only be 2 letter (iso 639-1) language code (e.g. \"he\")");
        panel3.add(LanguageSuffixField, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        openSubtitlesUsernameLabel = new JLabel();
        openSubtitlesUsernameLabel.setText("OpenSubtitles username:");
        openSubtitlesUsernameLabel.setToolTipText("your OpenSubtitles Username (NOT email)");
        panel3.add(openSubtitlesUsernameLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesUsernameField = new JTextField();
        openSubtitlesUsernameField.setToolTipText("");
        panel3.add(openSubtitlesUsernameField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        languageSuffixLabel = new JLabel();
        languageSuffixLabel.setText("Language suffix:");
        languageSuffixLabel.setToolTipText("can only be 2 letter (iso 639-1) language code (e.g. \"he\")");
        panel3.add(languageSuffixLabel, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesPasswordLabel = new JLabel();
        openSubtitlesPasswordLabel.setText("OpenSubtitles password:");
        openSubtitlesPasswordLabel.setToolTipText("Your OpenSubtitles password");
        panel3.add(openSubtitlesPasswordLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesPasswordField = new JPasswordField();
        panel3.add(openSubtitlesPasswordField, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        openSubtitlesApiKeyLabel = new JLabel();
        openSubtitlesApiKeyLabel.setText("OpenSubtitles API Key:");
        openSubtitlesApiKeyLabel.setToolTipText("Your OpenSubtitles API Key");
        panel3.add(openSubtitlesApiKeyLabel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesApiKeyField = new JTextField();
        openSubtitlesApiKeyField.setToolTipText("");
        panel3.add(openSubtitlesApiKeyField, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        openSubtitlesUserAgentLabel = new JLabel();
        openSubtitlesUserAgentLabel.setText("OpenSubtitles UserAgent:");
        openSubtitlesUserAgentLabel.setToolTipText("Your OpenSubtitles UserAgent (app name+version)");
        panel3.add(openSubtitlesUserAgentLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesUserAgentField = new JTextField();
        openSubtitlesUserAgentField.setToolTipText("");
        panel3.add(openSubtitlesUserAgentField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        logLevelLabel = new JLabel();
        logLevelLabel.setText("Log level:");
        logLevelLabel.setToolTipText("finest logs the most, and is the one to use when reporting a problem");
        panel3.add(logLevelLabel, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        logLevelComboBox = new JComboBox<>();
        final DefaultComboBoxModel<String> defaultComboBoxModel1 = new DefaultComboBoxModel<>();
        defaultComboBoxModel1.addElement("severe");
        defaultComboBoxModel1.addElement("warning");
        defaultComboBoxModel1.addElement("info");
        defaultComboBoxModel1.addElement("fine");
        defaultComboBoxModel1.addElement("finer");
        defaultComboBoxModel1.addElement("finest");
        logLevelComboBox.setModel(defaultComboBoxModel1);
        panel3.add(logLevelComboBox, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        watchIgnoreKeywordsLabel = new JLabel();
        watchIgnoreKeywordsLabel.setText("Watch ignore keywords:");
        watchIgnoreKeywordsLabel.setToolTipText("comma separated; new files whose path contains one of them are not searched");
        panel3.add(watchIgnoreKeywordsLabel, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        watchIgnoreKeywordsField = new JTextField();
        watchIgnoreKeywordsField.setToolTipText("comma separated; new files whose path contains one of them are not searched");
        panel3.add(watchIgnoreKeywordsField, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        watchDirectoriesLabel = new JLabel();
        watchDirectoriesLabel.setText("Watched folders:");
        watchDirectoriesLabel.setToolTipText("new video files in these folders are searched automatically");
        panel3.add(watchDirectoriesLabel, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        watchDirectoriesButton = new JButton();
        watchDirectoriesButton.setText("Edit...");
        panel3.add(watchDirectoriesButton, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
