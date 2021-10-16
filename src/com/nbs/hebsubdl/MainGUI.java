package com.nbs.hebsubdl;

import com.nbs.hebsubdl.SubProviders.FindSubs;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        MainGUI mainGUI = new MainGUI();
        JFrame frame = new JFrame("HebSubDL");
        frame.setContentPane(mainGUI.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        List<File> itemsList = null;

        mainGUI.pathToLoadTA.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedItems = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    fillItemsList(mainGUI,droppedItems);
                    frame.pack();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        mainGUI.browseButton.addActionListener(event -> browseForItems(mainGUI,frame,itemsList));
        mainGUI.goButton.addActionListener(event -> fillFilesTable(frame,mainGUI.filesTable, mainGUI.pathToLoadTA));
        SettingsDialog settingsDialog = new SettingsDialog();
        mainGUI.settingsButton.addActionListener(event -> settingsDialog.showDiag());

        frame.pack();
        frame.setVisible(true);
    }

    private static void fillItemsList(MainGUI mainGUI, List<File> itemsList) {
        int itemCount=0;
        for (File item : itemsList) {
            if (FilesAndFolders.isDraggedItemAllowed(item)) {
                if (itemCount==0)
                    mainGUI.pathToLoadTA.setText("");
                mainGUI.pathToLoadTA.append(item.toString()+"\n"); }
            itemCount++;
        }
    }
    private static void browseForItems(MainGUI mainGUI, JFrame frame, List<File> itemsList) {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.showOpenDialog(frame);
        File[] itemsArray = fileChooser.getSelectedFiles();
        itemsList = Arrays.asList(itemsArray);
        fillItemsList(mainGUI,itemsList);
        frame.pack();
    }
    private static void fillFilesTable (JFrame frame, JTable jTable, JTextArea jTextArea) {
        DefaultTableModel model = (DefaultTableModel)jTable.getModel();
        model.setRowCount(0);
        model.setColumnCount(0);
        model.addColumn(new Object[] {"file"});
        model.addColumn(new Object[] {"status"});
        model.addRow(new Object[] { "columns headers" });
        model.setValueAt("file",0,0);
        model.setValueAt("status",0,1);

        List<String> itemsList = Arrays.asList(jTextArea.getText().split("\\n"));
        ArrayList<String> filesList = new ArrayList<>();
        for (String item : itemsList) {
            if (Files.isDirectory(Paths.get(item))) {
                try {
                    FilesAndFolders.walkDir(item,filesList);
                } catch (IOException e) {
                    e.printStackTrace();
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

        Runnable getSubsThread = new Runnable() {
            public void run() {
                workOnFilesList(filesList,model, jTable);
            }
        };
        new Thread(getSubsThread).start();
        //workOnFilesList(filesList);

    }
    public static void fitColumns (JTable jTable) {
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
        ArrayList<MediaFile> mediaFilesList = new ArrayList<>();
        for (String file:filesList) {
            //for the meantime, just print the file list
            MediaFile mediaFile = new MediaFile(file);
            mediaFilesList.add(mediaFile);
            MediaFile.parse(mediaFile);
        }
        ImdbQuery.getImdbID(mediaFilesList);
        try {
            FindSubs.findSubs(mediaFilesList, model, jTable);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static PropertiesClass properties = new PropertiesClass();

}