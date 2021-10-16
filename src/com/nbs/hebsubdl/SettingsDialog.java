package com.nbs.hebsubdl;

import javax.swing.*;
import java.awt.event.*;
import java.util.HashMap;

public class SettingsDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField ktuvitUsernameField;
    private JPasswordField ktuvitPasswordField;
    private JTextField LanguageSuffixField;
    private JTextField openSubtitlesUAField;
    private JLabel ktuvitPasswordLabel;
    private JLabel ktuvitUsernameLabel;
    private JLabel languageSuffixLabel;
    private JLabel openSubtitlesUALabel;


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
        String openSubtitlesUA = openSubtitlesUAField.getText().trim();

        HashMap<String, String> properties = new HashMap<>();
        if (!ktuvitUsername.isEmpty())
            properties.put("ktuvitUsername", ktuvitUsername);
        if (!ktuvitPassword.isEmpty())
            properties.put("ktuvitPassword", ktuvitPassword);
        if (!langSuffix.isEmpty())
            properties.put("langSuffix", langSuffix);
        if (!openSubtitlesUA.isEmpty())
            properties.put("openSubtitlesUserAgent", openSubtitlesUA);
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
        openSubtitlesUAField.setText(PropertiesClass.getOpenSubtitlesUserAgent());
    }

}
