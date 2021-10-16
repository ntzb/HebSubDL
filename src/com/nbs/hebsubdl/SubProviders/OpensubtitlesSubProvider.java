package com.nbs.hebsubdl.SubProviders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbs.hebsubdl.Logger;
import com.nbs.hebsubdl.MediaFile;
import com.nbs.hebsubdl.PropertiesClass;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class OpensubtitlesSubProvider implements ISubProvider {
    private URL queryURL;
    private String language;

    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
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
        setLanguage("heb");
        StringBuilder url = new StringBuilder("https://rest.opensubtitles.org/search/"+
                "sublanguageid-"+getLanguage()+"/");
        if (!mediaFile.getImdbId().trim().isEmpty())
            url.append("imdbid-"+mediaFile.getImdbId().substring(2,9));
        else
            url.append("tag-"+mediaFile.getFileName().toLowerCase());
        if(!mediaFile.getEpisode().equals("0")) {
            DecimalFormat formatter = new DecimalFormat("0");
            String formattedSeason = formatter.format(Integer.parseInt(mediaFile.getSeason()));
            String formattedEpisode = formatter.format(Integer.parseInt(mediaFile.getEpisode()));
            url.append("/season-"+formattedSeason+"/"+"episode-"+formattedEpisode);
        }
        this.queryURL = new URL(url.toString());
    }

    @Override
    public String getQueryJsonResponse(URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("User-Agent",PropertiesClass.getOpenSubtitlesUserAgent());
        InputStream inputStream = urlConnection.getInputStream();
        String response = "";
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));) { //try with resources, so they will be closed when we are done.
            char[] readBuffer = new char[2048];
            int responseSize = bufferedReader.read(readBuffer); //let's read and see the response size
            while (responseSize > 0) {
                response = response + String.copyValueOf(readBuffer, 0, responseSize); //must specify the offset and count to read, else will end up with more garbage at the end of the read buffer
                responseSize = bufferedReader.read(readBuffer);
            }
        }
        return response;
    }

    @Override
    public boolean downloadSubFile(String subId, MediaFile mediaFile) throws IOException {
        File subZip = new File(FilenameUtils.removeExtension(mediaFile.getPathName()+"\\"+
                mediaFile.getFileName())+".zip");
        URL url = new URL(subId);
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream()); //try with resources
             FileOutputStream fos = new FileOutputStream(subZip)) {
            long bytesTransferred = fos.getChannel().transferFrom(rbc, 0, 1000000);
            if (bytesTransferred == 0)
                return false;
            else {
                StringBuilder subFileInZip = new StringBuilder();
                final String[] allowedSubExtensions = {"srt", "sub"};
                List<FileHeader> fileHeaders = new ZipFile(subZip).getFileHeaders();
                for (FileHeader fileHeader: fileHeaders) {
                    if (FilenameUtils.isExtension(fileHeader.getFileName(),allowedSubExtensions)) {
                        subFileInZip.append(fileHeader.getFileName());
                        break;
                    }
                }
                new ZipFile(subZip).extractFile(subFileInZip.toString(), mediaFile.getPathName());
                File extractedSubFile = new File(mediaFile.getPathName()+"\\"+subFileInZip.toString());
                File newSubFile = new File(mediaFile.getPathName()+"\\"+
                        FilenameUtils.removeExtension(mediaFile.getOriginalFileName())+'.'+ PropertiesClass.getLangSuffix()+'.'+
                        FilenameUtils.getExtension(extractedSubFile.toString()));
                if (!extractedSubFile.renameTo(newSubFile))
                    Logger.logger.warning("could not rename the file");
                fos.close();
                if (!subZip.delete())
                    Logger.logger.warning("can't delete the sub zip file!");
            }
        }
        return true;
    }

/*    @Override
    public void getSub (MediaFile mediaFile) throws IOException {
        generateQueryURL(mediaFile);
        QueryJsonResponse[] queryJsonResponses = mapJsonResponse(getQueryJsonResponse(getQueryURL()));
        downloadSubFile(getTitleRating(queryJsonResponses,mediaFile.getFileName()),mediaFile);
    }*/

    @Override
    public String[] getRating(MediaFile mediaFile, String[] titleWordsArray) throws IOException {
        generateQueryURL(mediaFile);
        QueryJsonResponse[] queryJsonResponses = mapJsonResponse(getQueryJsonResponse(getQueryURL()));
        String[] ratingResponseArray=getTitleRating(queryJsonResponses,titleWordsArray);
        return ratingResponseArray;
    }

    private QueryJsonResponse[] mapJsonResponse (String response) throws IOException {
        //ObjectMapper objectMapper = new ObjectMapper();
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(response,QueryJsonResponse[].class);
    }
    private String[] getTitleRating(QueryJsonResponse[] queryResponseArray, String[] titleWordsArray) {
        int maxRating = 0;
        String highestRatingLink ="";
        for (QueryJsonResponse queryJsonResponse:queryResponseArray) {
            String testedTitle = queryJsonResponse.MovieReleaseName.toLowerCase().trim();
            String[] testedTitleWordArray = testedTitle.replaceAll("_"," ").replaceAll
                    ("\\."," ").replaceAll("-"," ").split(" ");
            int rating = 0;
            for (String word:titleWordsArray) {
                if (Arrays.asList(testedTitleWordArray).contains(word))
                    rating++;
            }
            if (rating > maxRating) {
                maxRating = rating;
                highestRatingLink = queryJsonResponse.ZipDownloadLink;
            }
        }
        String[] titleRatingResponse={highestRatingLink,String.valueOf(maxRating)};
        return titleRatingResponse;
    }

    static class QueryJsonResponse {
        private String MovieReleaseName;
        private String ZipDownloadLink;

        public String getMovieReleaseName() {
            return MovieReleaseName;
        }
        @JsonProperty("MovieReleaseName")
        public void setMovieReleaseName(String MovieReleaseName) {
            this.MovieReleaseName = MovieReleaseName;
        }
        public String getZipDownloadLink() {
            return ZipDownloadLink;
        }
        @JsonProperty("ZipDownloadLink")
        public void setZipDownloadLink(String ZipDownloadLink) {
            this.ZipDownloadLink = ZipDownloadLink;
        }
    }
}
