package com.nbs.hebsubdl;

import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.Timer;


public class WatchDir {

    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
    private Timer timer = new Timer();
    TimerTask timerTask;
    private ArrayList<String> fileList = new ArrayList<>();
    // these will only be assigned once, at app startup
    private final JTable jTable;
    private final JFrame jFrame;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                Logger.logger.info(String.format("register: %s\n", dir));
            } else {
                if (!dir.equals(prev)) {
                    Logger.logger.info(String.format("update: %s -> %s\n", prev, dir));
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    WatchDir(List<String> dirs, boolean recursive, JFrame frame, JTable jTable) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;
        this.jTable = jTable;
        this.jFrame = frame;

        for (String dirStr : dirs) {
            Path dir = Paths.get(dirStr);
            if (recursive) {
                Logger.logger.info(String.format("scanning %s ...", dir));
                registerAll(dir);
                Logger.logger.info("done scanning and registering for changes.");
            } else {
                register(dir);
            }
        }

        // enable trace after initial registration
        this.trace = true;
    }

    /**
     * Process all events for keys queued to the watcher
     */
    void processEvents() {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                Logger.logException(x, "failed taking watch key.");
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                Logger.logger.warning("watchkey not recognized!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                // ignore "overflow" or "modify" types, we only want "create" or "delete"
                if (kind == OVERFLOW || kind == ENTRY_MODIFY) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // filter video files only, throw out other types or dirs
                if (!Files.isDirectory(child, NOFOLLOW_LINKS) && needToIgnoreFile(child))
                    continue;

                // do something with the event

                if (event.kind().name().equals("ENTRY_CREATE")) {
                    // handle new video file - add to db, search for subs
                    fileList.add(child.toString());
                    Logger.logger.info(String.format("adding %s to filelist.", child.toString()));

                    try {
                        Logger.logger.finer("cancelling existing timer.");
                        this.timer.cancel();
                        Logger.logger.finer("creating new timer.");
                        this.timer = new Timer();
                        timerTask = new TimerTask() {
                            @Override
                            public void run() {
                                searchForSubs();
                            }
                        };
                        timer.schedule(timerTask, 10 * 1000);
                    } catch (Exception e) {
                        Logger.logException(e, "timer issues:");
                    }

                }
                else {
                    // handle deletion - remove from db
                    System.out.format("%s: %s\n", event.kind().name(), child);
                }

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        Logger.logException(x, "failed checking for dir or registering all subdirs for change events.");
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void searchForSubs() {
        Logger.logger.info(String.format("searching for subtitles for %d files.", fileList.size()));
        String str = String.join("\n", fileList);
        fileList.clear();
        JTextArea jTextArea = new JTextArea(str);
        MainGUI.fillFilesTable(jFrame, jTable, jTextArea);
    }

    private boolean needToIgnoreFile(Path path) {
        String filename = path.toString();

        // see if extension is not allowed
        boolean allowed_extension = FilenameUtils.isExtension(filename, FilesAndFolders.allowedExtensions);
        if (!allowed_extension) {
            Logger.logger.finest(String.format("ignoring file %s because its extension is not allowed", filename));
            return true;
        }

        // see if name contains keywords to ignore
        String keywordsToIgnore = PropertiesClass.getWatchIgnoreKeywords();
        if (keywordsToIgnore == null || keywordsToIgnore.isBlank())
            return false;
        for (String keyword : keywordsToIgnore.split(",")) {
            if (filename.contains(keyword)) {
                Logger.logger.finest(String.format("ignoring file %s because it has a keyword set to be ignored", filename));
                return true;
            }
        }
        return false;
    }

}
