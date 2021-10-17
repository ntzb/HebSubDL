package com.nbs.hebsubdl;

import com.nbs.hebsubdl.SubProviders.FindSubs;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.stream.Collectors;


public class MainGUI {
    private JPanel mainPanel;
    private JLabel welcomeLabel;
    private JLabel instructionLabel;
    private JPanel workPanel;
    private JTextArea pathToLoadTA;
    private JButton browseButton;
    private JButton goButton;
    private JLabel pathLabel;
    private JTable filesTable;
    private JButton settingsButton;

    public static void main(String[] args) {
        Logger.initLogger();
        Logger.logger.info("Starting app.");
        MainGUI mainGUI = new MainGUI();
        JFrame frame = new JFrame("HebSubDL");
        frame.setContentPane(mainGUI.mainPanel);

        handleNonTrayExitCommand(frame);
        addTraySupport(frame);

        mainGUI.pathToLoadTA.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedItems = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    fillItemsList(mainGUI,droppedItems);
                    frame.pack();

                } catch (Exception ex) {
                    Logger.logException(ex, "dragging files into window.");
                }
            }
        });

        mainGUI.browseButton.addActionListener(event -> browseForItems(mainGUI,frame));
        mainGUI.goButton.addActionListener(event -> fillFilesTable(frame,mainGUI.filesTable, mainGUI.pathToLoadTA));
        SettingsDialog settingsDialog = new SettingsDialog();
        mainGUI.settingsButton.addActionListener(event -> settingsDialog.showDiag());

        frame.pack();
        frame.setVisible(true);

        setupDirWatcher(mainGUI, frame);
    }

    private static void setupDirWatcher(MainGUI mainGUI, JFrame frame) {
        String watchDirs = PropertiesClass.getWatchDirectories();
        List<String> dirs = Arrays.stream(watchDirs.split(",")).collect(Collectors.toList());
        try {
            if (!watchDirs.isBlank() && dirs.size() > 0)
                new WatchDir(dirs, true, frame, mainGUI.filesTable).processEvents();
        } catch (NoSuchFileException e) {
            Logger.logSevereAndExitWithError(String.format("directory asked to watch doesn't exists: %s, " +
                    "did you forget to use double backslash in the config file?", e.getMessage()));
        } catch (IOException e) {
            Logger.logException(e, "when registering directories to watch");
        }
    }

    private static void fillItemsList(MainGUI mainGUI, List<File> itemsList) {
        Logger.logger.finer("filling items list.");
        int itemCount=0;
        for (File item : itemsList) {
            if (FilesAndFolders.isDraggedItemAllowed(item)) {
                if (itemCount==0)
                    mainGUI.pathToLoadTA.setText("");
                mainGUI.pathToLoadTA.append(item +"\n"); }
            itemCount++;
        }
    }
    private static void browseForItems(MainGUI mainGUI, JFrame frame) {
        Logger.logger.finer("browsing for items");
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.showOpenDialog(frame);
        File[] itemsArray = fileChooser.getSelectedFiles();
        List<File> itemsList = Arrays.asList(itemsArray);
        fillItemsList(mainGUI, itemsList);
        frame.pack();
    }
    public static void fillFilesTable (JFrame frame, JTable jTable, JTextArea jTextArea) {
        Logger.logger.finer(String.format("filling files table with %s", jTextArea.getText()));
        DefaultTableModel model = (DefaultTableModel)jTable.getModel();
        model.setRowCount(0);
        model.setColumnCount(0);
        model.addColumn(new Object[] {"file"});
        model.addColumn(new Object[] {"status"});
        model.addRow(new Object[] { "columns headers" });
        model.setValueAt("file",0,0);
        model.setValueAt("status",0,1);

        String[] itemsList = jTextArea.getText().split("\\n");
        ArrayList<String> filesList = new ArrayList<>();
        for (String item : itemsList) {
            if (Files.isDirectory(Paths.get(item))) {
                try {
                    FilesAndFolders.walkDir(item,filesList);
                } catch (IOException e) {
                    Logger.logException(e, "walking directories to find video files.");
                }
            }
            else
                filesList.add(item);
        }
        int rowCount=1;
        for (String file : filesList) {
            model.addRow(new Object[] { file });
            model.setValueAt(file,rowCount,0);
            model.setValueAt("working...",rowCount,1);
            rowCount++;
        }
        fitColumns(jTable);
        frame.pack();

        Runnable getSubsThread = () -> workOnFilesList(filesList,model, jTable);
        new Thread(getSubsThread).start();
        //workOnFilesList(filesList);

    }
    public static void fitColumns (JTable jTable) {
        Logger.logger.finer("adjusting columns.");
        jTable.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );

        for (int column = 0; column < jTable.getColumnCount(); column++)
        {
            TableColumn tableColumn = jTable.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidth = tableColumn.getMaxWidth();

            for (int row = 0; row < jTable.getRowCount(); row++)
            {
                TableCellRenderer cellRenderer = jTable.getCellRenderer(row, column);
                Component c = jTable.prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + jTable.getIntercellSpacing().width;
                preferredWidth = Math.max(preferredWidth, width);

                //  We've exceeded the maximum width, no need to check other rows

                if (preferredWidth >= maxWidth)
                {
                    preferredWidth = maxWidth;
                    break;
                }
            }

            tableColumn.setPreferredWidth( preferredWidth );
        }

    }
    private static void workOnFilesList (ArrayList<String> filesList, DefaultTableModel model, JTable jTable) {
        Logger.logger.finer("working on file list.");
        ArrayList<MediaFile> mediaFilesList = new ArrayList<>();
        for (String file:filesList) {
            //for the meantime, just print the file list
            MediaFile mediaFile = new MediaFile(file);
            mediaFilesList.add(mediaFile);
            MediaFile.parse(mediaFile);
        }
        ImdbQuery.getImdbID(mediaFilesList);
        try {
            if (!mediaFilesList.isEmpty()) {
                Logger.logger.fine("starting subtitles search.");
                FindSubs.findSubs(mediaFilesList, model, jTable);
            }
            else
                Logger.logger.info("empty file list - nothing to do.");
        } catch (IOException e) {
            Logger.logException(e, "calling findSubs.");
        }
    }

    private static boolean showExitConfirmation(JFrame frame) {
        int result = JOptionPane.showConfirmDialog(
                frame,
                "Are you sure you want to exit the application?",
                "Exit Application",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            // we want to exit - cleanup handlers
            for (Handler h : Logger.logger.getHandlers())
                h.close();
            // set back the exit on close property so it will actually exit.
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return true;
        }
        return false;
    }

    private static void handleNonTrayExitCommand(JFrame frame) {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener( new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                JFrame frame = (JFrame)e.getSource();
                showExitConfirmation(frame);
            }
        });
    }

    private static void addTraySupport(JFrame frame) {
        TrayIcon trayIcon;
        SystemTray tray;

        if (!SystemTray.isSupported())
            Logger.logger.finer("system tray not supported.");

        else {
            Logger.logger.finer("system tray supported.");
            tray = SystemTray.getSystemTray();

            // set tray icon
            Image image = Toolkit.getDefaultToolkit().getImage(MainGUI.class.getResource("/resources/sub_icon.png"));

            // set right click options - open and exit
            PopupMenu popup = new PopupMenu();
            MenuItem defaultItem = new MenuItem("Open");
            defaultItem.addActionListener(e -> {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
            });
            popup.add(defaultItem);

            defaultItem = new MenuItem("Exit");
            defaultItem.addActionListener(e -> {
                Logger.logger.finer("exiting.");
                if (showExitConfirmation(frame))
                    System.exit(0);
            });
            popup.add(defaultItem);

            // double click action
            trayIcon = new TrayIcon(image, "HebSubDL", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
            });

            // actual minimize/restore action
            SystemTray finalTray = tray;
            TrayIcon finalTrayIcon = trayIcon;
            frame.addWindowStateListener(e -> {
                if (e.getNewState() == Frame.ICONIFIED || e.getNewState() == 7) {
                    // 7 is ICONIFIED + MAXIMIZED_BOTH
                    try {
                        finalTray.add(finalTrayIcon);
                        frame.setVisible(false);
                        Logger.logger.finest("added to SystemTray.");
                    } catch (AWTException ex) {
                        Logger.logger.warning("unable to add to tray.");
                    }
                }
                if (e.getNewState() == Frame.NORMAL || e.getNewState() == Frame.MAXIMIZED_BOTH) {
                    finalTray.remove(finalTrayIcon);
                    frame.setVisible(true);
                    Logger.logger.finest("Tray icon removed.");
                }
            });
            // set app icon
            frame.setIconImage(Toolkit.getDefaultToolkit().getImage(MainGUI.class.getResource("/resources/sub_icon.png")));
        }
    }
}