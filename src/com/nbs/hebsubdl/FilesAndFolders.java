package com.nbs.hebsubdl;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.function.Predicate;

public class FilesAndFolders {
    public static String[] allowedExtensions = {"mkv", "mp4", "avi"};
    public static boolean isDraggedItemAllowed(File item) {
        return (FilenameUtils.isExtension(item.toString(),allowedExtensions) || item.isDirectory());
    }
    static private Predicate<? super Path> isFileValid = filePath -> (FilenameUtils.isExtension(filePath.toString(),allowedExtensions));
    static private boolean isFileValid2(Path path) {
        return(FilenameUtils.isExtension(path.toString(),allowedExtensions));
    }
    public static void walkDir(String path, ArrayList<String> filesList) throws IOException {
        Files.walk(Paths.get(path)).filter(isFileValid).forEach(file -> filesList.add(file.toString()));
        //System.out.println(filesList);
        //Files.walk(Paths.get(path)).filter(FilesAndFolders::isFileValid2).forEach(System.out::println);
        //Files.walk(Paths.get(path)).filter(Files::isRegularFile).forEach(System.out::println);
    }
}
