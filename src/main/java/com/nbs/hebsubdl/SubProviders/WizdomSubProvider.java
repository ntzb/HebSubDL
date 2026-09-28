package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public class WizdomSubProvider implements ISubProvider {
    private URL queryURL;
    String chosenSubName;
    boolean isHebrewOnly = true;

    @Override
    public String getChosenSubName() {
        return chosenSubName;
    }

    @Override
    public URL getQueryURL() {
        return queryURL;
    }

    @Override
    public void setQueryURL(URL queryURL) {
        this.queryURL = queryURL;
    }

    @Override
    public void generateQueryURL(MediaFile mediaFile) throws MalformedURLException {
        this.setQueryURL(new URL("http://wizdom.xyz/api/search?action=by_id&imdb=" + mediaFile.getImdbId() +
                "&season=" + mediaFile.getSeason() + "&episode=" + mediaFile.getEpisode() +
                "&version=" + mediaFile.getFileName()));
    }

    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        try {
            HttpURLConnection urlConnection = Throttle.WIZDOM.send(
                    () -> Throttle.withTimeouts((HttpURLConnection) url.openConnection()));
            // Wizdom answers 404 or 500 when it simply has nothing for the title
            if (urlConnection.getResponseCode() != 200) {
                Logger.logger.fine("Wizdom search answered " + urlConnection.getResponseCode());
                urlConnection.disconnect();
                return null;
            }
            InputStream inputStream = urlConnection.getInputStream();
            String response = "";
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) { // try with resources, so they will
                                                                                   // be closed when we are done.
                char[] readBuffer = new char[2048];
                int responseSize = bufferedReader.read(readBuffer); // let's read and see the response size
                while (responseSize > 0) {
                    response = response + String.copyValueOf(readBuffer, 0, responseSize); // must specify the offset
                                                                                           // and count to read, else
                                                                                           // will end up with more
                                                                                           // garbage at the end of the
                                                                                           // read buffer
                    responseSize = bufferedReader.read(readBuffer);
                }
            }
            return (response.equals("[]") ? null : response);
        } catch (java.io.FileNotFoundException e) {
            Logger.logException(e, "getting Wizdom query response.");
            return null;
        }
    }

    private QueryJsonResponse[] mapJsonResponse(String response) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        return objectMapper.readValue(response, QueryJsonResponse[].class);
    }

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        URL url = new URL("http://wizdom.xyz/api/files/sub/" + subId);
        HttpURLConnection con = Throttle.WIZDOM.send(() -> Throttle.withTimeouts((HttpURLConnection) url.openConnection()));
        if (con == null || con.getResponseCode() != 200) {
            Logger.logger.warning("Wizdom download failed" + (con == null ? "" : " with status " + con.getResponseCode()));
            if (con != null)
                con.disconnect();
            return false;
        }
        // a private folder per download: episodes of one season download in
        // parallel into the same folder, and their zips may hold same-named files
        Path workDir = Files.createTempDirectory("hebsubdl-wizdom");
        try {
            File subZip = workDir.resolve("sub.zip").toFile();
            long bytesTransferred;
            try (ReadableByteChannel rbc = Channels.newChannel(con.getInputStream()); // try with resources
                    FileOutputStream fos = new FileOutputStream(subZip)) {
                bytesTransferred = fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }
            if (bytesTransferred == 0)
                return false;

            try (ZipFile zip = new ZipFile(subZip)) {
                String entry = null;
                String extension = null;
                for (FileHeader fileHeader : zip.getFileHeaders()) {
                    String name = FilenameUtils.getName(fileHeader.getFileName());
                    String ext = FilenameUtils.getExtension(name).toLowerCase();
                    if (!fileHeader.isDirectory() && (ext.equals("srt") || ext.equals("sub"))) {
                        entry = fileHeader.getFileName();
                        extension = ext;
                        break;
                    }
                }
                if (entry == null) {
                    Logger.logger.warning("the Wizdom zip holds no srt/sub file");
                    return false;
                }
                zip.extractFile(entry, workDir.toString(), "extracted." + extension);
                File newSubFile = new File(String.format("%s/%s%s.%s", mediaFile.getPathName(),
                        FilenameUtils.removeExtension(mediaFile.getOriginalFileName()), PropertiesClass.getLangSuffix(),
                        extension));
                try {
                    Files.move(workDir.resolve("extracted." + extension), newSubFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // e.g. a player has the old subtitle open; let the next provider try
                    Logger.logException(e, "saving the Wizdom subtitle as " + newSubFile);
                    return false;
                }
            }
        } finally {
            deleteQuietly(workDir);
        }
        return true;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            Logger.logger.fine("could not clean up " + dir);
        }
    }

    static class QueryJsonResponse {
        public String versioname;
        public String id;

    }

    private int getTitleRating(String[] titleWordArray, String matchedTitle) {
        String[] testedTitleWordArray = matchedTitle.toLowerCase()
                .replaceAll("dd.{0,2}(2.{0,2}(0|1))", "dd20")
                .replaceAll("dd.{0,2}(5.{0,2}(0|1))", "dd50")
                .replace("web-dl", "webdl")
                .replaceAll("_", " ").replaceAll("\\.", " ").replaceAll("-", " ").split(" ");
        chosenSubName = matchedTitle;
        int rating = 0;
        for (String word : titleWordArray) {
            if (Arrays.asList(testedTitleWordArray).contains(word))
                rating++;
        }
        return rating;
    }

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        // wizdom only works with imdb. if a file doesn't have one, we'll not even
        // search..
        String[] ratingResponseArray = { "0", "0" };
        if (mediaFile.getImdbId().equals(""))
            return ratingResponseArray;
        generateQueryURL(mediaFile);
        String response = getQueryJsonResponse(getQueryURL());
        if (response == null || response.trim().isEmpty())
            return ratingResponseArray;
        QueryJsonResponse newResponse = mapJsonResponse(response)[0];
        ratingResponseArray[0] = newResponse.id;
        ratingResponseArray[1] = String.valueOf(getTitleRating(titleWordsArray, newResponse.versioname));
        return ratingResponseArray;
    }
}
