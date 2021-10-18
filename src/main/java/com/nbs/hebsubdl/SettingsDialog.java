package com.nbs.hebsubdl;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;

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

        HashMap<String, String> properties = new HashMap<>();
        //if (!ktuvitUsername.isEmpty())
        properties.put("ktuvitUsername", ktuvitUsername);
        //if (!ktuvitPassword.isEmpty())
        properties.put("ktuvitPassword", ktuvitPassword);
        //if (!langSuffix.isEmpty())
        properties.put("langSuffix", (langSuffix.isEmpty() || langSuffix.startsWith(".")) ? langSuffix : "." + langSuffix);
        //if (!openSubtitlesUsername.isEmpty())
        properties.put("openSubtitlesUsername", openSubtitlesUsername);
        //if (!openSubtitlesPassword.isEmpty())
        properties.put("openSubtitlesPassword", openSubtitlesPassword);
        PropertiesClass.writeProperties(properties);

        /*try {
            InputStream inputStream = new FileInputStream("config.properties");

            //load current properties
            Properties properties = new Properties();
            properties.load(inputStream);

            OutputStream outputStream = new FileOutputStream("config.properties");

            // set the properties value
            properties.setProperty("ktuvit.date", Long.toString(System.currentTimeMillis()));

            if (!ktuvitUsername.trim().isEmpty())
                properties.setProperty("ktuvit.username", ktuvitUsername);
            if (!ktuvitPassword.trim().isEmpty())
                properties.setProperty("ktuvit.password", ktuvitPassword);

            if (!LanguageSuffixField.getText().trim().isEmpty())
                properties.setProperty("langSuffix", LanguageSuffixField.getText());

            // save properties to project root folder
            properties.store(outputStream, null);

        } catch (IOException io) {
            io.printStackTrace();
        }*/

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
    }

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
        panel3.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        ktuvitUsernameLabel = new JLabel();
        ktuvitUsernameLabel.setText("Ktuvit username:");
        panel3.add(ktuvitUsernameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
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
        LanguageSuffixField.setToolTipText("can be \"he\", \"heb\", or whatever you'd like");
        panel3.add(LanguageSuffixField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        openSubtitlesUsernameLabel = new JLabel();
        openSubtitlesUsernameLabel.setText("OpenSubtitles username:");
        openSubtitlesUsernameLabel.setToolTipText("your OpenSubtitles Username (email)");
        panel3.add(openSubtitlesUsernameLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesUsernameField = new JTextField();
        openSubtitlesUsernameField.setToolTipText("your OpenSubtitles UserAgent string. see github ReadMe for more info.");
        panel3.add(openSubtitlesUsernameField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        languageSuffixLabel = new JLabel();
        languageSuffixLabel.setText("Language suffix:");
        languageSuffixLabel.setToolTipText("can be \"he\", \"heb\", or whatever you'd like");
        panel3.add(languageSuffixLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesPasswordLabel = new JLabel();
        openSubtitlesPasswordLabel.setText("OpenSubtitles password:");
        panel3.add(openSubtitlesPasswordLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openSubtitlesPasswordField = new JPasswordField();
        panel3.add(openSubtitlesPasswordField, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
